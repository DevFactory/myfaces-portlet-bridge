/* Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.myfaces.portlet.faces.util.map;

import java.util.Enumeration;

/**
 * Map of portlet request headers
 */
public class PortletRequestHeaderMap extends PortletAbstractMap
{
  private final PortletRequestHeaders mPortletRequestHeaders;

  public PortletRequestHeaderMap(PortletRequestHeaders portletRequestHeaders)
  {
    mPortletRequestHeaders = portletRequestHeaders;
  }

  @Override
  protected Object getAttribute(String key)
  {
    return mPortletRequestHeaders.getHeader(key);
  }

  @Override
  protected void setAttribute(String key, Object value)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void removeAttribute(String key)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  protected Enumeration getAttributeNames()
  {
    return mPortletRequestHeaders.getHeaderNames();
  }
}
