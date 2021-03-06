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

package com.epam.ta.reportportal.ws.converter.converters;

import com.epam.ta.reportportal.database.entity.user.RestorePasswordBid;
import com.epam.ta.reportportal.ws.model.user.RestorePasswordRQ;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Pavel_Bortnik
 */
public class RestorePasswordBidConverterTest {

    @Test(expected = NullPointerException.class)
    public void testNull() {
        RestorePasswordBidConverter.TO_BID.apply(null);
    }

    @Test
    public void testConvert() {
        RestorePasswordRQ rq = new RestorePasswordRQ();
        rq.setEmail("email@email.com");
        RestorePasswordBid bid = RestorePasswordBidConverter.TO_BID.apply(rq);
        Assert.assertEquals(bid.getEmail(), rq.getEmail());
        Assert.assertNotNull(bid.getId());
    }
}