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

package org.apache.myfaces.portlet.faces.util;

import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A class encapsulating an HTTP query string.
 */
public final class QueryString
{
  private String mQueryString;
  private String mCharacterEncoding;
  private Map    mParameterMap;
  private List   mParameterList;
  private List   mParameterNames;

  /**
   * Construct a <code>QueryString</code> from a pre-encoded string.
   */
  public QueryString(String queryString, String characterEncoding)
  {
    mQueryString = queryString;
    mCharacterEncoding = characterEncoding;
  }

  /**
   * Makes a copy of an existing <code>QueryString</code>.
   */
  public QueryString(QueryString source)
  {
    mQueryString = source.mQueryString;
    mCharacterEncoding = source.mCharacterEncoding;
    if (source.mParameterList != null)
    {
      mParameterList = new ArrayList(source.mParameterList);
    }
  }

  /**
   * Constructs an empty query string (parameters may be added later).
   */
  public QueryString(String characterEncoding)
  {
    mCharacterEncoding = characterEncoding;
  }

  /**
   * Constructs a query string from an old-fashioned array of PRE-ENCODED name-value pairs
   */
  public QueryString(String[][] args, String characterEncoding)
  {
    this(characterEncoding);
    for (String[] element : args)
    {
      addParameter(element[0], element[1], true);
    }
  }

  /**
   * Constructs a query string from a list of PRE-ENCODED name-value pairs
   */
  public QueryString(List params, String characterEncoding)
  {
    this(characterEncoding);

    Iterator pairs = params.iterator();
    while (pairs.hasNext())
    {
      String[] pair = (String[]) pairs.next();
      addParameter(pair[0], pair[1], true);
    }
  }

  /**
   * Converts this object into an encoded query string.
   */
  @Override
  public String toString()
  {
    // Use appendTo to concatenate the parameters together
    if (mQueryString == null)
    {
      appendTo(new SimpleStringBuffer(200));
    }
    return mQueryString;
  }

  /**
   * Appends the contents of this object to the given buffer in encoded query string form.
   * 
   * @param buff
   *          the buffer to append to
   */
  public void appendTo(SimpleStringBuffer buff)
  {
    // If we don't have a cached query string yet, generate it
    if (mQueryString == null)
    {
      // Remember the start position in the buffer, so that we can also
      // cache the
      // concatenated string in mQueryString
      int startPos = buff.length();
      Iterator i;
      if (mParameterList != null && (i = mParameterList.iterator()).hasNext())
      {
        Parameter param = (Parameter) i.next();
        buff.append(param.getEncodedName()).append('=').append(param.getEncodedValue());
        while (i.hasNext())
        {
          param = (Parameter) i.next();
          buff.append('&').append(param.getEncodedName()).append('=')
              .append(param.getEncodedValue());
        }
        mQueryString = buff.substring(startPos);
      }
      // If we don't have any parameters at all, cache the empty string
      else
      {
        mQueryString = "";
      }
    }
    // If we have a cached query string, reuse it
    else
    {
      buff.append(mQueryString);
    }
  }

  public Enumeration getParameterNames()
  {
    initParameterMap();
    return Collections.enumeration(mParameterNames);
  }

  public String getParameter(String name)
  {
    initParameterMap();
    List values = (List) mParameterMap.get(name);
    return values == null ? null : ((Parameter) values.get(0)).getValue();
  }

  public Enumeration getParameterValues(String name)
  {
    initParameterMap();
    List params = (List) mParameterMap.get(name);
    if (params == null)
    {
      return Collections.enumeration(Collections.EMPTY_LIST);
    }
    List values = new ArrayList(params.size());
    Iterator i = params.iterator();
    Parameter param;
    while (i.hasNext())
    {
      param = (Parameter) i.next();
      values.add(param.getValue());
    }
    return Collections.enumeration(values);
  }

  public void addParameter(String name, String value)
  {
    addParameter(name, value, false);
  }

  public void addParameter(String name, String value, boolean isEncoded)
  {
    if (value == null)
    {
      return;
    }
    initParameterList();

    // Invalidate the query string
    mQueryString = null;

    // Update the parameter list
    Parameter param = new Parameter(name, value, isEncoded);
    mParameterList.add(param);

    // Update the parameter map if it is initialized
    if (mParameterMap != null)
    {
      String decodedName = param.getName();
      List values = (List) mParameterMap.get(decodedName);
      if (values == null)
      {
        values = new ArrayList(4);
        mParameterMap.put(decodedName, values);
        // Only add UNIQUE parameter names (preserving order)
        mParameterNames.add(decodedName);
      }
      values.add(param);
    }
  }

  public void setParameter(String name, String value)
  {
    setParameter(name, value, false);
  }

  public void setParameter(String name, String value, boolean isEncoded)
  {
    if (value == null)
    {
      removeParameter(name, isEncoded);
      return;
    }
    initParameterMap();

    // Invalidate the query string
    mQueryString = null;

    // Update the map
    Parameter param = new Parameter(name, value, isEncoded);
    String decodedName = param.getName();
    List values = (List) mParameterMap.get(decodedName);
    if (values == null)
    {
      values = new ArrayList(4);
      mParameterMap.put(decodedName, values);
      // Only add UNIQUE parameter names (preserving order)
      mParameterNames.add(decodedName);
      mParameterList.add(param);
    }
    else
    {
      values.clear();

      // First, replace the existing occurence of the parameter
      int i = mParameterList.indexOf(param);
      mParameterList.set(i, param);

      // Now, remove any subsequent occurrences
      int j;
      while ((j = mParameterList.lastIndexOf(param)) > i)
      {
        mParameterList.remove(j);
      }
    }
    values.add(param);
  }

  public String removeParameter(String name)
  {
    return removeParameter(name, false);
  }

  public String removeParameter(String name, boolean isEncoded)
  {
    initParameterList();

    // Invalidate the query string
    mQueryString = null;

    // Create a template parameter for comparisons, so that we can avoid
    // decoding all parameter names in the list
    Parameter templateParam = new Parameter(name, "", isEncoded);

    // Update the parameter list
    Iterator i = mParameterList.iterator();
    Parameter param = null, firstParam = null;
    while (i.hasNext())
    {
      param = (Parameter) i.next();
      // Compare the parameter with our template (only the template name
      // will
      // be encoded / decoded if necessary)
      if (templateParam.equals(param))
      {
        if (firstParam == null)
        {
          firstParam = param;
        }
        i.remove();
      }
    }

    // Update the map, if it is initialized and we found a parameter
    if (mParameterMap != null && firstParam != null)
    {
      String decodedName = templateParam.getName();
      List values = (List) mParameterMap.remove(decodedName);
      if (values != null)
      {
        mParameterNames.remove(decodedName);
      }
    }

    return firstParam == null ? null : isEncoded ? firstParam.getEncodedValue()
                                                : firstParam.getValue();
  }

  private void initParameterMap()
  {
    if (mParameterMap == null)
    {
      initParameterList();

      mParameterMap = new HashMap(30);
      mParameterNames = new ArrayList(30);
      if (mParameterList.size() == 0)
      {
        return;
      }
      String decodedName;
      Parameter param;
      List values;
      Iterator i = mParameterList.iterator();
      while (i.hasNext())
      {
        param = (Parameter) i.next();
        decodedName = param.getName();
        values = (List) mParameterMap.get(decodedName);
        if (values == null)
        {
          values = new ArrayList(4);
          mParameterMap.put(decodedName, values);
          // Only add UNIQUE parameter names (preserving order)
          mParameterNames.add(decodedName);
        }
        values.add(param);
      }
    }
  }

  private void initParameterList()
  {
    if (mParameterList == null)
    {
      mParameterList = new ArrayList(30);
      int length;
      if (mQueryString == null || (length = mQueryString.length()) == 0)
      {
        return;
      }
      Parameter param;
      int lastPos = 0, nextPos, sepPos;
      do
      {
        nextPos = mQueryString.indexOf('&', lastPos);
        if (nextPos == -1)
        {
          nextPos = length;
        }
        sepPos = mQueryString.indexOf('=', lastPos);
        if (sepPos != -1 && sepPos < nextPos)
        {
          param = new Parameter(mQueryString.substring(lastPos, sepPos),
                                mQueryString.substring(sepPos + 1, nextPos), true);
        }
        else
        {
          param = new Parameter(mQueryString.substring(lastPos, nextPos), "", true);
        }
        mParameterList.add(param);
        lastPos = nextPos + 1;
      } while (nextPos < length);
    }
  }

  private class Parameter
  {
    private String mName;
    private String mEncodedName;

    private String mValue;
    private String mEncodedValue;

    public Parameter(String name, String value, boolean encoded)
    {
      if (encoded)
      {
        mEncodedName = name;
        mEncodedValue = value;
      }
      else
      {
        mName = name;
        mValue = value;
      }
    }

    public String getName()
    {
      if (mName == null)
      {
        try
        {
          mName = HTTPUtils.decode(mEncodedName, mCharacterEncoding);
        }
        catch (UnsupportedEncodingException uee)
        {
          handleUnsupportedEncoding();
        }
      }
      return mName;
    }

    public String getEncodedName()
    {
      if (mEncodedName == null)
      {
        try
        {
          mEncodedName = HTTPUtils.encode(mName, mCharacterEncoding);
        }
        catch (UnsupportedEncodingException uee)
        {
          handleUnsupportedEncoding();
        }
      }
      return mEncodedName;
    }

    public String getValue()
    {
      if (mValue == null)
      {
        try
        {
          mValue = HTTPUtils.decode(mEncodedValue, mCharacterEncoding);
        }
        catch (UnsupportedEncodingException uee)
        {
          handleUnsupportedEncoding();
        }
      }
      return mValue;
    }

    public String getEncodedValue()
    {
      if (mEncodedValue == null)
      {
        try
        {
          mEncodedValue = HTTPUtils.encode(mValue, mCharacterEncoding);
        }
        catch (UnsupportedEncodingException uee)
        {
          handleUnsupportedEncoding();
        }
      }
      return mEncodedValue;
    }

    /**
     * Compares two parameters for name equality.
     * 
     * Attempts not to invoke any lazy encoding or decoding in the passed in parameter - only in
     * this one.
     */
    @Override
    public boolean equals(Object o)
    {
      if (o == null || !(o instanceof Parameter))
      {
        return false;
      }
      Parameter p1 = (Parameter) o;
      return p1.mName != null && getName().equals(p1.mName) || p1.mEncodedName != null
             && getEncodedName().equals(p1.mEncodedName);
    }
  }

  private void handleUnsupportedEncoding()
  {
    throw new IllegalArgumentException(
                                       new SimpleStringBuffer(100)
                                                                  .append(
                                                                          "Unrecognized character encoding \"")
                                                                  .append(mCharacterEncoding)
                                                                  .append('"').toString());
  }
}
