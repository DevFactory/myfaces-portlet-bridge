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
public abstract class PortletAbstractMap<V> implements Map<String, V>
{
  static final String ILLEGAL_ARGUMENT = "Only supported in a portlet environment";

  private Set<String> mKeySet;
  private Collection<V> mValues;
  private Set<Entry<String, V>> mEntrySet;

  public void clear()
  {
    List<String> names = new ArrayList<String>();
    for (Enumeration<String> e = getAttributeNames(); e.hasMoreElements();)
    {
      names.add(e.nextElement());
    }

    for (String name : names)
    {
      removeAttribute(name);
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

    for (Enumeration<String> e = getAttributeNames(); e.hasMoreElements();)
    {
      Object value = getAttribute(e.nextElement());
      if (findValue.equals(value))
      {
        return true;
      }
    }

    return false;
  }

  public Set<Entry<String, V>> entrySet()
  {
    return mEntrySet != null ? mEntrySet : (mEntrySet = new EntrySet());
  }

  public V get(Object key)
  {
    return getAttribute(key.toString());
  }

  public boolean isEmpty()
  {
    return !getAttributeNames().hasMoreElements();
  }

  public Set<String> keySet()
  {
    return mKeySet != null ? mKeySet : (mKeySet = new KeySet());
  }

  public V put(String key, V value)
  {
    String localKey = key.toString();
    V retval = getAttribute(localKey);
    setAttribute(localKey, value);
    return retval;
  }
  
  public void putAll(Map<? extends String, ? extends V> t)
  {
    for (Entry<? extends String, ? extends V> entry : t.entrySet())
    {
      setAttribute(entry.getKey(), entry.getValue());
    }
  }

  public V remove(Object key)
  {
    String localKey = key.toString();
    V retval = getAttribute(localKey);
    removeAttribute(localKey);
    return retval;
  }

  public int size()
  {
    int size = 0;
    for (Enumeration<String> e = getAttributeNames(); e.hasMoreElements();)
    {
      size++;
      e.nextElement();
    }
    
    return size;
  }

  public Collection<V> values()
  {
    return mValues != null ? mValues : (mValues = new Values());
  }

  protected abstract V getAttribute(String key);

  protected abstract void setAttribute(String key, V value);

  protected abstract void removeAttribute(String key);

  protected abstract Enumeration<String> getAttributeNames();
  
  private abstract class BaseMapContentSet<T> extends AbstractSet<T>
  {
    @Override
    public void clear()
    {
      PortletAbstractMap.this.clear();
    }
    
    @Override
    public int size()
    {
      return PortletAbstractMap.this.size();
    }
  }

  private class KeySet extends BaseMapContentSet<String>
  {
    @Override
    public boolean contains(Object o)
    {
      return containsKey(o);
    }

    @Override
    public Iterator<String> iterator()
    {
      return new KeyIterator();
    }

    @Override
    public boolean remove(Object o)
    {
      return PortletAbstractMap.this.remove(o) != null;
    }
  }

  private class KeyIterator implements Iterator<String>
  {
    protected final Enumeration<String> mEnum = getAttributeNames();
    protected String mKey;

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

    public String next()
    {
      return mKey = mEnum.nextElement();
    }
  }
  
  private abstract class KeyIteratorWrapper<T> implements Iterator<T>
  {
    private KeyIterator wrapped;
    
    protected KeyIteratorWrapper()
    {
      wrapped = new KeyIterator();
    }
    
    public boolean hasNext()
    {
      return wrapped.hasNext();
    }

    protected String nextKey()
    {
      return wrapped.next();
    }

    public void remove()
    {
      wrapped.remove();
    }
    
  }

  private class Values extends BaseMapContentSet<V>
  {
    @Override
    public boolean contains(Object o)
    {
      return containsValue(o);
    }

    @Override
    public Iterator<V> iterator()
    {
      return new ValuesIterator();
    }

    @Override
    public boolean remove(Object o)
    {
      if (o == null)
      {
        return false;
      }

      for (Iterator<V> it = iterator(); it.hasNext();)
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

  private class ValuesIterator extends KeyIteratorWrapper<V>
  {
    public V next()
    {
      return get(nextKey());
    }
  }

  private class EntrySet extends BaseMapContentSet<Entry<String, V>>
  {
    @Override
    public Iterator<Entry<String, V>> iterator()
    {
      return new EntryIterator();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o)
    {
      if (!(o instanceof Entry))
      {
        return false;
      }

      Entry<String, V> entry = (Entry<String, V>)o;
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key == null || value == null)
      {
        return false;
      }

      return value.equals(get(key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(Object o)
    {
      if (!(o instanceof Entry))
      {
        return false;
      }

      Entry<String, V> entry = (Entry<String, V>) o;
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key == null || value == null || !value.equals(get(key)))
      {
        return false;
      }

      return PortletAbstractMap.this.remove(key) != null;
    }
  }

  private class EntryIterator extends KeyIteratorWrapper<Entry<String, V>>
  {
    public Entry<String, V> next()
    {
      return new EntrySetEntry(nextKey());
    }
  }

  private class EntrySetEntry implements Entry<String, V>
  {
    private final String mKey;

    public EntrySetEntry(String currentKey)
    {
      mKey = currentKey;
    }

    public String getKey()
    {
      return mKey;
    }

    public V getValue()
    {
      return get(mKey);
    }

    public V setValue(V value)
    {
      return put(mKey, value);
    }
  }
}
