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

import javax.portlet.PortletContext;

/**
 * Map of portlet context attributes
 */
public class PortletApplicationMap extends PortletAbstractMap
{
  private final PortletContext mPortletContext;

  public PortletApplicationMap(Object context)
  {
    if (context instanceof PortletContext)
    {
      mPortletContext = (PortletContext) context;
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  @Override
  public Object getAttribute(String key)
  {
    if (mPortletContext != null)
    {
      return mPortletContext.getAttribute(key);
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  @Override
  public void setAttribute(String key, Object value)
  {
    if (mPortletContext != null)
    {
      mPortletContext.setAttribute(key, value);
    }
  }

  @Override
  public void removeAttribute(String key)
  {
    if (mPortletContext != null)
    {
      mPortletContext.removeAttribute(key);
    }
  }

  @Override
  public Enumeration getAttributeNames()
  {
    if (mPortletContext != null)
    {
      return mPortletContext.getAttributeNames();
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }
}
