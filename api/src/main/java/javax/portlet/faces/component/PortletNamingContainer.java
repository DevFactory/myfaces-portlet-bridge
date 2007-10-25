/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */
package javax.portlet.faces.component;

import javax.faces.component.NamingContainer;

/**
 * <p>
 * <strong>PortletNamingContainer</strong> is an interface that must be implemented by any
 * UIViewRoot that wants to be used in a Portlet Bridge environment. It indicates that this the
 * component (usually a UIViewRoot) that implements this <code>NamingContainer</code> incorporates
 * the consumer provided portlet namespaceId into the id returned from
 * <code>getContainerClientId</code>. This is merely a marker interface
 */
public interface PortletNamingContainer extends NamingContainer
{

}
