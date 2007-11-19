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

import javax.portlet.PortletRequest;

/**
 * Map of portlet request attributes
 */
public class PortletRequestMap extends PortletAbstractMap
{
  private final PortletRequest mPortletRequest;

  public PortletRequestMap(Object request)
  {
    if (request instanceof PortletRequest)
    {
      mPortletRequest = (PortletRequest) request;
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  @Override
  public Object getAttribute(String key)
  {
    if (mPortletRequest != null)
    {
      return mPortletRequest.getAttribute(key);
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  @Override
  public void setAttribute(String key, Object value)
  {
    if (mPortletRequest != null)
    {

      mPortletRequest.setAttribute(key, value);

    }
  }

  @Override
  public void removeAttribute(String key)
  {
    if (mPortletRequest != null)
    {
      mPortletRequest.removeAttribute(key);
    }
  }

  @Override
  public Enumeration getAttributeNames()
  {
    if (mPortletRequest != null)
    {
      return mPortletRequest.getAttributeNames();
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }
}
