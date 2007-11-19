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

package org.apache.myfaces.portlet.faces.bridge.wrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.portlet.RenderRequest;

public class BridgeRenderRequestWrapper extends RenderRequestDecorator
{
  private Map mActionParams     = null;
  private Map mCombinedParamMap = null;

  public BridgeRenderRequestWrapper(RenderRequest request, Map actionParams)
                                                                            throws IllegalArgumentException
  {
    super(request);

    mActionParams = actionParams;
  }

  /**
   * Returns the value of a request parameter as a <code>String</code>, or <code>null</code> if
   * the parameter does not exist. Request parameters are extra information sent with the request.
   * The returned parameter are "x-www-form-urlencoded" decoded.
   * <p>
   * Only parameters targeted to the current portlet are accessible.
   * <p>
   * This method should only be used if the parameter has only one value. If the parameter might
   * have more than one value, use {@link #getParameterValues}.
   * <p>
   * If this method is used with a multivalued parameter, the value returned is equal to the first
   * value in the array returned by <code>getParameterValues</code>.
   * 
   * 
   * 
   * @param name
   *          a <code>String</code> specifying the name of the parameter
   * 
   * @return a <code>String</code> representing the single value of the parameter
   * 
   * @see #getParameterValues
   * 
   * @exception java.lang.IllegalArgumentException
   *              if name is <code>null</code>.
   * 
   */
  @Override
  public String getParameter(String name)
  {
    String[] params = getParameterValues(name);
    if (params != null && params.length > 0)
    {
      return params[0];
    }
    else
    {
      return null;
    }
  }

  /**
   * 
   * Returns an <code>Enumeration</code> of <code>String</code> objects containing the names of
   * the parameters contained in this request. If the request has no parameters, the method returns
   * an empty <code>Enumeration</code>.
   * <p>
   * Only parameters targeted to the current portlet are returned.
   * 
   * 
   * @return an <code>Enumeration</code> of <code>String</code> objects, each
   *         <code>String</code> containing the name of a request parameter; or an empty
   *         <code>Enumeration</code> if the request has no parameters.
   */
  @Override
  public Enumeration getParameterNames()
  {
    final Enumeration e = Collections.enumeration(getParameterMap().entrySet());
    Enumeration en = new Enumeration() {
      public boolean hasMoreElements()
      {
        return e.hasMoreElements();
      }

      public Object nextElement()
      {
        Map.Entry entry = (Map.Entry) e.nextElement();
        return entry.getKey();
      }
    };

    return en;
  }

  /**
   * Returns an array of <code>String</code> objects containing all of the values the given
   * request parameter has, or <code>null</code> if the parameter does not exist. The returned
   * parameters are "x-www-form-urlencoded" decoded.
   * <p>
   * If the parameter has a single value, the array has a length of 1.
   * 
   * 
   * @param name
   *          a <code>String</code> containing the name of the parameter the value of which is
   *          requested
   * 
   * @return an array of <code>String</code> objects containing the parameter values.
   * 
   * @see #getParameter
   * 
   * @exception java.lang.IllegalArgumentException
   *              if name is <code>null</code>.
   * 
   */
  @Override
  public String[] getParameterValues(String name)
  {
    if (name == null)
    {
      throw new IllegalArgumentException();
    }

    return (String[]) getParameterMap().get(name);

  }

  /**
   * Returns a <code>Map</code> of the parameters of this request. Request parameters are extra
   * information sent with the request. The returned parameters are "x-www-form-urlencoded" decoded.
   * <p>
   * The values in the returned <code>Map</code> are from type String array (<code>String[]</code>).
   * <p>
   * If no parameters exist this method returns an empty <code>Map</code>.
   * 
   * @return an immutable <code>Map</code> containing parameter names as keys and parameter values
   *         as map values, or an empty <code>Map</code> if no parameters exist. The keys in the
   *         parameter map are of type String. The values in the parameter map are of type String
   *         array (<code>String[]</code>).
   */
  @Override
  public java.util.Map getParameterMap()
  {
    if (mActionParams != null && !mActionParams.isEmpty())
    {
      if (mCombinedParamMap == null)
      {
        mCombinedParamMap = new LinkedHashMap(getParent().getParameterMap());

        // now walk through the actionParams adding those that aren't
        // already in the ParameterMap
        Set s = mActionParams.entrySet();
        if (s != null)
        {
          Iterator entries = s.iterator();
          while (entries != null && entries.hasNext())
          {
            Map.Entry entry = (Map.Entry) entries.next();
            String key = (String) entry.getKey();
            if (!mCombinedParamMap.containsKey(key))
            {
              mCombinedParamMap.put(key, entry.getValue());
            }
          }
          // now make this an immutable Map
          mCombinedParamMap = Collections.unmodifiableMap(mCombinedParamMap);
        }
      }
      return mCombinedParamMap;
    }
    else
    {
      return null;
    }
  }

}
