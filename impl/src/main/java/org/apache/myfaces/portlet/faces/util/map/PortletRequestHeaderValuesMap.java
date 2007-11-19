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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Map of portlet request header values
 */
public class PortletRequestHeaderValuesMap extends PortletAbstractMap
{
  private final PortletRequestHeaders mPortletRequestHeaders;
  private final Map                   mValueCache = new HashMap();

  public PortletRequestHeaderValuesMap(PortletRequestHeaders portletRequestHeaders)
  {
    mPortletRequestHeaders = portletRequestHeaders;
  }

  @Override
  protected Object getAttribute(String key)
  {
    Object ret = mValueCache.get(key);
    if (ret == null)
    {
      mValueCache.put(key, ret = toArray(mPortletRequestHeaders.getHeaders(key)));
    }
    return ret;
  }

  @Override
  protected void setAttribute(String key, Object value)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void removeAttribute(String key)
  {
    throw new UnsupportedOperationException("");
  }

  @Override
  protected Enumeration getAttributeNames()
  {
    return mPortletRequestHeaders.getHeaderNames();
  }

  private String[] toArray(Enumeration e)
  {
    List ret = new ArrayList();

    while (e.hasMoreElements())
    {
      ret.add(e.nextElement());
    }

    return (String[]) ret.toArray(new String[ret.size()]);
  }
}
