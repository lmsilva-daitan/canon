package org.symphonyoss.s2.canon.runtime.cache;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.symphonyoss.s2.canon.runtime.Producer;

public class WeakCache<K,V>
{
  private Map<K, WeakReference<V>> map_           = new HashMap<>();
  private ReferenceQueue<V>        queue_         = new ReferenceQueue<>();
  private Thread                   discardThread_ = new DiscardThread();
  private Producer<V>              producer_;

  public WeakCache()
  {
    this(null);
  }
  
	public WeakCache(Producer<V> producer)
  {
	  producer_ = producer;
	  discardThread_.start();
  }

  class DiscardThread extends Thread
	{
		private DiscardThread()
		{
			super("WeakCache-Discard");
			setDaemon(true);
		}

		@Override
		public void run()
		{
			while(true)
			{
				try
				{
					Reference<? extends V> ref = queue_.remove();
					
					if(ref instanceof WeakCacheReference)
					{
					  // we can do this because Map.remove() takes an Object
					  map_.remove(((WeakCacheReference<?,?>)ref).getKey());
					}
				}
				catch(InterruptedException e)
				{
					// Ignored
				}
			}
		}
	}

  
  public V  fetch(K k)
  {
    WeakReference<V> ref = map_.get(k);
  		
		if(ref != null)
		{
			V v = ref.get();
			
			if(v != null)
			{
			  return v;
			}
		}
		
		return null;
  }
	
	public synchronized Collection<V>	getAllCached()
	{
	  Collection<V>	list = new LinkedList<>();
		
		for(WeakReference<V> ref : map_.values())
		{
			V v = ref.get();
				
			if(v != null)
			{
				list.add(v);
			}
		}
		
		return list;
	}
	
	/**
	 * Cache the given object as the current version unless it is in the
	 * cache already, in which case return the existing value.
	 * 
	 * In order to minimize synchronization when we load an object from
	 * persistent storage we look in the cache, if it is not there we
	 * load it from storage and then call  this method. If 2 threads try to
	 * load the same object at the same time they will both deserialize the
	 * object but one of them gets discarded as after loading both
	 * threads call this method.
	 * @param key    The key.
	 * @param value  The object.
	 * @param notify If true then notify all listeners.
	 * 
	 * @return The "one true instance" of that object.
	 */
	public synchronized V cache(K key, V value, boolean notify)
  {
	  V existing = fetch(key);
    
    if(existing != null)
    {
      return existing;
    }
    else
    {
      put(key, value, notify);

      return value;
    }
  }

  protected void put(K key, V value, boolean notify)
  {
    if(notify && producer_ != null)
      producer_.notifyListeners(value);
    
    map_.put(key, new WeakCacheReference<K,V>(key, value, queue_));
  }
}
