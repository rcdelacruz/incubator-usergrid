/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  *  contributor license agreements.  The ASF licenses this file to You
 *  * under the Apache License, Version 2.0 (the "License"); you may not
 *  * use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.  For additional information regarding
 *  * copyright in this work, please see the NOTICE file in the top level
 *  * directory of this distribution.
 *
 */
package org.apache.usergrid.rest.test.resource2point0.endpoints.mgmt;

import com.sun.jersey.api.client.WebResource;
import org.apache.usergrid.rest.test.resource2point0.endpoints.NamedResource;
import org.apache.usergrid.rest.test.resource2point0.endpoints.UrlResource;
import org.apache.usergrid.rest.test.resource2point0.model.ApiResponse;
import org.apache.usergrid.rest.test.resource2point0.model.Credentials;
import org.apache.usergrid.rest.test.resource2point0.model.QueryParameters;
import org.apache.usergrid.rest.test.resource2point0.state.ClientContext;

import javax.ws.rs.core.MediaType;

/**
 */
public class CredentialsResource extends NamedResource {

    public CredentialsResource(final ClientContext context, final UrlResource parent) {
        super("credentials", context, parent);
    }

    public Credentials get(final QueryParameters parameters, final boolean useToken) {
        WebResource resource = getResource(useToken);
        resource = addParametersToResource(resource, parameters);
        ApiResponse response = resource.type(MediaType.APPLICATION_JSON_TYPE).accept(MediaType.APPLICATION_JSON)
            .get(ApiResponse.class);
        return new Credentials(response);
    }

    public Credentials get(final QueryParameters parameters) {
        return get(parameters, true);
    }

    public Credentials get() {
        return get(null, true);
    }
}
