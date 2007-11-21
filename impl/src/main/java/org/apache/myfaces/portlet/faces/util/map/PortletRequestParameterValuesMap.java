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
 */

package org.apache.myfaces.portlet.faces.util.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.portlet.PortletRequest;

/**
 * Map of portlet request param values
 */
public class PortletRequestParameterValuesMap extends PortletAbstractMap<String[]>
{
  private final PortletRequest mPortletRequest;
  private final Map<String, String[]> mInternalAttributes;

  public PortletRequestParameterValuesMap(Object request, Map<String, String[]> internal)
  {
    if (request instanceof PortletRequest)
    {
      mPortletRequest = (PortletRequest) request;
      if (internal == null)
      {
        mInternalAttributes = Collections.emptyMap();
      }
      else
      {
        mInternalAttributes = internal;
      }
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  @Override
  public String[] getAttribute(String key)
  {
    if (mPortletRequest != null)
    {
      String[] value = mInternalAttributes.get(key);
      if (value != null)
      {
        return value;
      }

      return mPortletRequest.getParameterValues(key);
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }

  @Override
  public void setAttribute(String key, String[] value)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeAttribute(String key)
  {
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Enumeration<String> getAttributeNames()
  {
    if (mPortletRequest != null)
    {
      // merged list of internal parameters & request parameters
      List<String> attrNames = new ArrayList<String>(5);

      Enumeration<String> requestAttrNames = mPortletRequest.getParameterNames();
      while (requestAttrNames.hasMoreElements())
      {
        attrNames.add(requestAttrNames.nextElement());
      }

      attrNames.addAll(mInternalAttributes.keySet());

      return Collections.enumeration(attrNames);
    }
    else
    {
      throw new IllegalArgumentException(ILLEGAL_ARGUMENT);
    }
  }
}
