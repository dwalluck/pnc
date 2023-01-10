/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.remotecoordinator.rexclient.provider;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.pnc.remotecoordinator.rexclient.exception.TaskNotFoundException;
import org.jboss.pnc.rex.dto.responses.ErrorResponse;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Slf4j
public class TaskNotFoundMapper implements ResponseExceptionMapper<TaskNotFoundException> {

    @Override
    public TaskNotFoundException toThrowable(Response response) {
        var error = response.readEntity(ErrorResponse.class);
        log.error("Resource not found. Rex Exception: {}, Message: {}", error.errorType, error.errorMessage);
        return new TaskNotFoundException(error.errorMessage);
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status == NOT_FOUND.getStatusCode();
    }
}
