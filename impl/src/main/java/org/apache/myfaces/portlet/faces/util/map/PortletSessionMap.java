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

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import javax.portlet.faces.Bridge;

/**
 * Map of portlet session attributes
 */
public class PortletSessionMap extends PortletAbstractMap
{

  private final PortletRequest mPortletRequest;
  private final int            mScope;

  public PortletSessionMap(Object request)
  {
    if (request != null && request instanceof PortletRequest)
    {
      mPortletRequest = (PortletRequest) request;
      mScope = PortletSession.PORTLET_SCOPE;
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  public PortletSessionMap(Object request, int scope)
  {
    if (request != null && request instanceof PortletRequest)
    {
      mPortletRequest = (PortletRequest) request;
      mScope = scope;
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  @Override
  protected Object getAttribute(String key)
  {
    PortletSession portletSession = mPortletRequest.getPortletSession(true);

    if (key.equals(Bridge.APPLICATION_SCOPE_MAP))
    {
      return getAppScopeMap(portletSession);
    }
    else
    {
      return portletSession.getAttribute(key, mScope);
    }
  }

  @Override
  protected void setAttribute(String key, Object value)
  {
    if (mPortletRequest != null)
    {
      mPortletRequest.getPortletSession(true).setAttribute(key, value, mScope);
    }
  }

  @Override
  protected void removeAttribute(String key)
  {
    if (mPortletRequest != null)
    {
      PortletSession portletSession = mPortletRequest.getPortletSession(false);

      if (portletSession != null)
      {
        portletSession.removeAttribute(key, mScope);
      }
    }
  }

  @Override
  protected Enumeration getAttributeNames()
  {
    if (mPortletRequest != null)
    {
      PortletSession portletSession = mPortletRequest.getPortletSession(false);
      ;
      return portletSession == null ? Collections.enumeration(Collections.EMPTY_LIST)
                                   : portletSession.getAttributeNames(mScope);
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  private Map getAppScopeMap(PortletSession portletSession)
  {
    if (mScope != PortletSession.PORTLET_SCOPE)
    {
      return null;
    }

    Map m = (Map) portletSession.getAttribute(Bridge.APPLICATION_SCOPE_MAP);

    if (m == null)
    {
      m = new PortletSessionMap(mPortletRequest, PortletSession.APPLICATION_SCOPE);
      portletSession.setAttribute(Bridge.APPLICATION_SCOPE_MAP, m);
    }
    return m;
  }

}
