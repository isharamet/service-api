/*
 * Copyright 2017 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/service-api
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.epam.ta.reportportal.util.analyzer;

import com.epam.ta.reportportal.database.dao.LaunchRepository;
import com.epam.ta.reportportal.database.dao.LogRepository;
import com.epam.ta.reportportal.database.dao.TestItemRepository;
import com.epam.ta.reportportal.database.entity.Launch;
import com.epam.ta.reportportal.database.entity.Log;
import com.epam.ta.reportportal.database.entity.item.TestItem;
import com.epam.ta.reportportal.util.analyzer.model.IndexLaunch;
import com.epam.ta.reportportal.util.analyzer.model.IndexRs;
import com.epam.ta.reportportal.util.analyzer.model.IndexRsItem;
import com.epam.ta.reportportal.util.analyzer.model.IndexTestItem;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ILogIndexer}.
 *
 * @author Ivan Sharamet
 *
 */
@Service("indexerService")
public class LogIndexerService implements ILogIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogIndexerService.class);

    private static final int BATCH_SIZE = 1000;

    private static final String CHECKPOINT_COLL = "logIndexingCheckpoint";
    private static final String CHECKPOINT_ID = "checkpoint";
    private static final String CHECKPOINT_LOG_ID = "logId";

    @Autowired
    private AnalyzerServiceClient analyzerServiceClient;

    @Autowired
    private MongoOperations mongoOperations;

    @Autowired
    private LaunchRepository launchRepository;

    @Autowired
    private TestItemRepository testItemRepository;

    @Autowired
    private LogRepository logRepository;

    public void indexLogs(String launchId, List<TestItem> testItems) {
        Launch launch = launchRepository.findEntryById(launchId);
        if (launch != null) {
            IndexLaunch rqLaunch = new IndexLaunch();
            rqLaunch.setLaunchId(launchId);
            rqLaunch.setLaunchName(launch.getName());
            List<IndexTestItem> rqTestItems = new ArrayList<>(testItems.size());
            for (TestItem testItem : testItems) {
                List<Log> logs = logRepository.findByTestItemRef(testItem.getId());
                rqTestItems.add(IndexTestItem.fromTestItem(testItem, logs));
            }
        }
    }

    public void indexAllLogs() {
        String checkpoint = getLastCheckpoint();

        try (CloseableIterator<Log> logIterator = getLogIterator(checkpoint)) {
            List<IndexLaunch> rq = new ArrayList<>(BATCH_SIZE);
            while (logIterator.hasNext()) {
                Log log = logIterator.next();
                IndexLaunch rqLaunch = createRqLaunch(log);
                if (rqLaunch != null) {
                    if (checkpoint == null) {
                        checkpoint = log.getId();
                    }
                    rq.add(rqLaunch);
                    if (rq.size() == BATCH_SIZE || !logIterator.hasNext()) {
                        createCheckpoint(checkpoint);

                        IndexRs rs = analyzerServiceClient.index(rq);

                        List<IndexRsItem> failedItems =
                                rs.getItems().stream().filter(i -> i.failed()).collect(Collectors.toList());

                        // TODO: Retry failed items!

                        rq = new ArrayList<>(BATCH_SIZE);
                        checkpoint = null;
                    }
                }
            }
        }

        getCheckpointCollection().drop();
    }

    private CloseableIterator<Log> getLogIterator(String checkpoint) {
        Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "_id"));
        Query query = new Query().with(sort).noCursorTimeout();

        if (checkpoint != null) {
            query.addCriteria(Criteria.where("_id").gte(new ObjectId(checkpoint)));
        }

        return mongoOperations.stream(query, Log.class);
    }

    private IndexLaunch createRqLaunch(Log log) {
        IndexLaunch rqLaunch = null;
        TestItem testItem = testItemRepository.findOne(log.getTestItemRef());
        if (testItem != null) {
            Launch launch = launchRepository.findOne(testItem.getLaunchRef());
            if (launch != null) {
                rqLaunch = new IndexLaunch();
                rqLaunch.setLaunchId(launch.getId());
                rqLaunch.setLaunchName(launch.getName());
                rqLaunch.setTestItems(
                        Collections.singletonList(
                                IndexTestItem.fromTestItem(
                                        testItem, Collections.singletonList(log))));
            }
        }
        return rqLaunch;
    }

    private DBCollection getCheckpointCollection() {
        return mongoOperations.getCollection(CHECKPOINT_COLL);
    }

    private String getLastCheckpoint() {
        DBObject checkpoint = getCheckpointCollection().findOne(new BasicDBObject("_id", CHECKPOINT_ID));
        return checkpoint == null ? null : (String) checkpoint.get(CHECKPOINT_LOG_ID);
    }

    private void createCheckpoint(String logId) {
        BasicDBObject checkpoint = new BasicDBObject("_id", CHECKPOINT_ID).append(CHECKPOINT_LOG_ID, logId);
        getCheckpointCollection().save(checkpoint);
    }
}


