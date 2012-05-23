/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.hibernate.ogm.helper;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javassist.Modifier;

import org.hibernate.ogm.datastore.spi.TupleSnapshot;
import org.hibernate.ogm.grid.EntityKey;
import org.hibernate.ogm.helper.annotation.AnnotationFinder;
import org.hibernate.ogm.util.impl.Log;
import org.hibernate.ogm.util.impl.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * @author Seiya Kawashima <skawashima@uchicago.edu>
 */
public class JSONHelper {

	private static final Log log = LoggerFactory.make();
	private final AnnotationFinder finder = new AnnotationFinder();
	private final Gson gson = new GsonBuilder().registerTypeAdapter( Date.class, new JsonSerializer<Date>() {
		@Override
		public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive( src.getTime() );
		}
	} ).registerTypeAdapter( Date.class, new JsonDeserializer<Date>() {

		public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			return new Date( json.getAsLong() );
		}
	} ).registerTypeAdapter( Calendar.class, new JsonSerializer<Calendar>() {

		@Override
		public JsonElement serialize(Calendar src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive( src.getTimeInMillis() );
		}

	} ).registerTypeAdapter( Calendar.class, new JsonDeserializer<Calendar>() {

		@Override
		public Calendar deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis( json.getAsLong() );
			return calendar;
		}

	} ).registerTypeAdapter( GregorianCalendar.class, new JsonSerializer<GregorianCalendar>() {

		@Override
		public JsonElement serialize(GregorianCalendar src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive( src.getTimeInMillis() );
		}

	} ).registerTypeAdapter( UUID.class, new JsonSerializer<UUID>() {

		@Override
		public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive( src.toString() );
		}

	} ).create();

	/**
	 * Creates JSON representation based on the specified object.
	 * 
	 * @param obj
	 *            To be JSONed.
	 * @return JSON representation of the specified object.
	 */
	public String toJSON(Object obj) {
		return gson.toJson( obj );
	}

	/**
	 * Creates Object from the specified JSON representation based on the
	 * specified Class.
	 * 
	 * @param json
	 *            To be turned to Object.
	 * @param cls
	 *            Used to turn the JSON to object.
	 * @return Object representation of the JSON.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Object fromJSON(String json, Class cls) {
		if ( json == null || json.equals( "null" ) ) {
			return null;
		}

		return gson.fromJson( json, cls );
	}

	/**
	 * Converts both key and value to Json.
	 * 
	 * @param map
	 *            Map to be converted to Jsoned key and value pairs.
	 * @return Jsoned map.
	 */
	@SuppressWarnings("rawtypes")
	public Map<String, String> convertKeyAndValueToJsonOn(Map map) {
		Map<String, String> jsonedMap = new HashMap<String, String>();
		for ( Iterator itr = map.keySet().iterator(); itr.hasNext(); ) {
			Object k = itr.next();
			jsonedMap.put( toJSON( k ), toJSON( map.get( k ) ) );
		}

		return jsonedMap;
	}

	/**
	 * Gets object from JSON when the specified field type is one of JSONed type
	 * as the specified columnName on the specified Map.
	 * 
	 * @param field
	 *            Corresponding field to the columnName.
	 * @param columnName
	 *            Column name used on the datastore.
	 * @param map
	 *            Stores entity objects.
	 */
	public void getObjectFromJsonOn(Class cls, String columnName, Map<String, Object> map) {

		try {
			map.put( columnName, fromJSON( (String) map.get( columnName ), cls ) );
		}
		catch ( JsonParseException ex ) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Converts the value for the column as JSON format.
	 * 
	 * @param columnNames
	 *            All the columnNames in the entity object.
	 * @return Newly created Map storing JSON format when required.
	 * @throws ClassNotFoundException
	 */
	public Map<String, Object> convertJsonAsNeededOn(Set<String> columnNames, TupleSnapshot snapshot) {

		Map<String, Object> map = new HashMap<String, Object>();

		for ( String columnName : columnNames ) {
			map.put( columnName, toJSON( snapshot.get( columnName ) ) );
		}
		return map;
	}

	/**
	 * Converts Json to object representation for the return value from the
	 * datastore when needed.
	 * 
	 * @param key
	 *            Used to retrieve the corresponding value.
	 * @param tuple
	 *            Contains key value pairs from the datastore.
	 * @return Retrieved key value pairs with JSON modification when needed.
	 */
	public Map<String, Object> convertFromJsonOn(EntityKey key, Map<String, Object> tuple, Field[] fields) {

		Map<String, Class> map = null;
		for ( Field field : fields ) {
			if ( Modifier.isTransient( field.getModifiers() ) ) {
				continue;
			}

			Object obj = tuple.get( field.getName() );
			if ( obj != null ) {
				if ( finder.isEntityAnnotated( field.getType() ) ) {
					if ( isReturnAsString( field.getType() ) ) {
						getObjectFromJsonOn( String.class, field.getName(), tuple );
					}
					else {
						getObjectFromJsonOn( obj.getClass(), field.getName(), tuple );
					}
				}
				else {
					if ( isReturnAsString( field.getType() ) ) {
						getObjectFromJsonOn( String.class, field.getName(), tuple );
					}
					else {
						getObjectFromJsonOn( field.getType(), field.getName(), tuple );
					}
				}
			}
			else {
				if ( finder.isEntityAnnotated( field.getType() ) ) {
					log.info( "this field has some kind of association, field: " + field.getName() );

					map = finder.findAllJoinColumnNamesFrom( field.getType(), "", true );
					if ( map.isEmpty() ) {
						map = createKeys( field.getName(), finder.findAllIdsFrom( field.getType(), "", true ), "_" );
					}
					if ( !map.isEmpty() ) {
						for ( Iterator<Entry<String, Class>> itr = map.entrySet().iterator(); itr.hasNext(); ) {
							Entry<String, Class> entry = itr.next();
							if ( tuple.get( entry.getKey() ) != null ) {
								getObjectFromJsonOn( String.class, entry.getKey(), tuple );
							}
						}
					}
				}
				else if ( finder.isEmbeddableAnnotated( field.getType() ) ) {
					log.info( "this field has @Embeddable and also has field and column mapping field: " + field.getName() + " " + field.getType() );
					for(Field f: field.getType().getDeclaredFields()){
						if(tuple.get( field.getName() + "." + f.getName() ) != null){
							getObjectFromJsonOn(String.class,field.getName() + "." + f.getName(),tuple);
						}
					}
					
					map = finder.findAllColumnNamesFrom( field.getType(), "", false );
					for(Iterator<Entry<String,Class>> itr = map.entrySet().iterator();itr.hasNext();){
						Entry<String,Class> entry = itr.next();
						getObjectFromJsonOn(entry.getValue(),entry.getKey(),tuple);
					}
					map = finder.findAllJoinColumnNamesFrom( field.getType(), "", false );
					for(Iterator<Entry<String,Class>> itr = map.entrySet().iterator();itr.hasNext();){
						Entry<String,Class> entry = itr.next();
						getObjectFromJsonOn(entry.getValue(),entry.getKey(),tuple);
					}
				}
				else {
					log.info( "this field has some kind of field and column mapping,field: " + field.getName() );
					map = finder.findAllColumnNamesFrom( field.getDeclaringClass(), field.getName(), true );

					for ( Iterator<Entry<String, Class>> itr = map.entrySet().iterator(); itr.hasNext(); ) {
						Entry<String, Class> ent = itr.next();
						String columnName = ent.getKey();
						if ( isReturnAsString( field.getType() ) ) {
							getObjectFromJsonOn( String.class, columnName, tuple );
						}
						else {
							if ( tuple.get( columnName ) != null ) {
								getObjectFromJsonOn( field.getType(), columnName, tuple );
							}
							else {
								map = finder.findAllIdsFrom( field.getType(), "", true );
								if ( !map.isEmpty() ) {
									log.info( "no association. found id: " + map );
									for ( Iterator<Entry<String, Class>> it = map.entrySet().iterator(); itr.hasNext(); ) {
										Entry<String, Class> entry = it.next();
										if ( tuple.get( entry.getKey() ) != null ) {
											getObjectFromJsonOn( entry.getValue(), entry.getKey(), tuple );
										}
									}
								}
							}
						}
					}
				}
			}
		}

		return tuple;
	}
	
	private Map<String,Class> createKeys(String fieldName, Map<String,Class> map,String separator){
		
		Map<String,Class> keyMap = new HashMap<String,Class>();
		for(Iterator<Entry<String,Class>> itr = map.entrySet().iterator();itr.hasNext();){
			Entry<String,Class> entry = itr.next();
			keyMap.put( fieldName + separator + entry.getKey(), entry.getValue() );
		}
		return keyMap;
	}
	
	private boolean isReturnAsString(Class cls){
		
		if ( cls.getCanonicalName().equals( "java.util.UUID" )
				|| cls.getCanonicalName().equals( "java.math.BigDecimal" )
				|| cls.getCanonicalName().equals( "java.net.URL" )
				|| cls.getCanonicalName().equals( "java.math.BigInteger" ) ) {
			return true;
		}
		
		return false;
	}
}
