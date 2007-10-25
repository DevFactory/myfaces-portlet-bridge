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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Helper class for different portlet external context maps
 */
public abstract class PortletAbstractMap implements Map
{
  static final String ILLEGAL_ARGUMENT = "Only supported in a portlet environment";

  private Set         mKeySet;
  private Collection  mValues;
  private Set         mEntrySet;

  public void clear()
  {
    List names = new ArrayList();
    for (Enumeration e = getAttributeNames(); e.hasMoreElements();)
    {
      names.add(e.nextElement());
    }

    for (Iterator it = names.iterator(); it.hasNext();)
    {
      removeAttribute((String) it.next());
    }
  }

  public boolean containsKey(Object key)
  {
    return getAttribute(key.toString()) != null;
  }

  public boolean containsValue(Object findValue)
  {
    if (findValue == null)
    {
      return false;
    }

    for (Enumeration e = getAttributeNames(); e.hasMoreElements();)
    {
      Object value = getAttribute((String) e.nextElement());
      if (findValue.equals(value))
      {
        return true;
      }
    }

    return false;
  }

  public Set entrySet()
  {
    return mEntrySet != null ? mEntrySet : (mEntrySet = new EntrySet());
  }

  public Object get(Object key)
  {
    return getAttribute(key.toString());
  }

  public boolean isEmpty()
  {
    return !getAttributeNames().hasMoreElements();
  }

  public Set keySet()
  {
    return mKeySet != null ? mKeySet : (mKeySet = new KeySet());
  }

  public Object put(Object key, Object value)
  {
    String localKey = key.toString();
    Object retval = getAttribute(localKey);
    setAttribute(localKey, value);
    return retval;
  }

  public void putAll(Map t)
  {
    for (Iterator it = t.entrySet().iterator(); it.hasNext();)
    {
      Entry entry = (Entry) it.next();
      setAttribute(entry.getKey().toString(), entry.getValue());
    }
  }

  public Object remove(Object key)
  {
    String localKey = key.toString();
    Object retval = getAttribute(localKey);
    removeAttribute(localKey);
    return retval;
  }

  public int size()
  {
    int size = 0;
    for (Enumeration e = getAttributeNames(); e.hasMoreElements();)
    {
      size++;
      e.nextElement();
    }
    return size;
  }

  public Collection values()
  {
    return mValues != null ? mValues : (mValues = new Values());
  }

  protected abstract Object getAttribute(String key);

  protected abstract void setAttribute(String key, Object value);

  protected abstract void removeAttribute(String key);

  protected abstract Enumeration getAttributeNames();

  private class KeySet extends AbstractSet
  {
    @Override
    public Iterator iterator()
    {
      return new KeyIterator();
    }

    @Override
    public boolean isEmpty()
    {
      return PortletAbstractMap.this.isEmpty();
    }

    @Override
    public int size()
    {
      return PortletAbstractMap.this.size();
    }

    @Override
    public boolean contains(Object o)
    {
      return containsKey(o);
    }

    @Override
    public boolean remove(Object o)
    {
      return PortletAbstractMap.this.remove(o) != null;
    }

    @Override
    public void clear()
    {
      PortletAbstractMap.this.clear();
    }
  }

  private class KeyIterator implements Iterator
  {
    protected final Enumeration mEnum = getAttributeNames();
    protected Object            mKey;

    public void remove()
    {
      if (mKey == null)
      {
        throw new NoSuchElementException();
      }
      PortletAbstractMap.this.remove(mKey);
    }

    public boolean hasNext()
    {
      return mEnum.hasMoreElements();
    }

    public Object next()
    {
      return mKey = mEnum.nextElement();
    }
  }

  private class Values extends KeySet
  {
    @Override
    public Iterator iterator()
    {
      return new ValuesIterator();
    }

    @Override
    public boolean contains(Object o)
    {
      return containsValue(o);
    }

    @Override
    public boolean remove(Object o)
    {
      if (o == null)
      {
        return false;
      }

      for (Iterator it = iterator(); it.hasNext();)
      {
        if (o.equals(it.next()))
        {
          it.remove();
          return true;
        }
      }

      return false;
    }
  }

  private class ValuesIterator extends KeyIterator
  {
    @Override
    public Object next()
    {
      super.next();
      return get(mKey);
    }
  }

  private class EntrySet extends KeySet
  {
    @Override
    public Iterator iterator()
    {
      return new EntryIterator();
    }

    @Override
    public boolean contains(Object o)
    {
      if (!(o instanceof Entry))
      {
        return false;
      }

      Entry entry = (Entry) o;
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key == null || value == null)
      {
        return false;
      }

      return value.equals(get(key));
    }

    @Override
    public boolean remove(Object o)
    {
      if (!(o instanceof Entry))
      {
        return false;
      }

      Entry entry = (Entry) o;
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key == null || value == null || !value.equals(get(key)))
      {
        return false;
      }

      return PortletAbstractMap.this.remove(((Entry) o).getKey()) != null;
    }
  }

  private class EntryIterator extends KeyIterator
  {
    @Override
    public Object next()
    {
      super.next();
      return new EntrySetEntry(mKey);
    }
  }

  private class EntrySetEntry implements Entry
  {
    private final Object mKey;

    public EntrySetEntry(Object currentKey)
    {
      mKey = currentKey;
    }

    public Object getKey()
    {
      return mKey;
    }

    public Object getValue()
    {
      return get(mKey);
    }

    public Object setValue(Object value)
    {
      return put(mKey, value);
    }
  }
}
