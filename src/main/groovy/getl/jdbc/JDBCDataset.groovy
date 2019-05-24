/*
 GETL - based package in Groovy, which automates the work of loading and transforming data. His name is an acronym for "Groovy ETL".

 GETL is a set of libraries of pre-built classes and objects that can be used to solve problems unpacking,
 transform and load data into programs written in Groovy, or Java, as well as from any software that supports
 the work with Java classes.
 
 Copyright (C) 2013-2015  Alexsey Konstantonov (ASCRUS)

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Lesser General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License and
 GNU Lesser General Public License along with this program.
 If not, see <http://www.gnu.org/licenses/>.
*/

package getl.jdbc

import getl.exception.ExceptionGETL
import groovy.transform.InheritConstructors
import getl.data.*
import getl.utils.*

/**
 * Base JDBC dataset class 
 * @author Alexsey Konstantinov
 *
 */
@InheritConstructors
class JDBCDataset extends Dataset {
	/**
	 * Type of jdbc datasets
	 */
	public static enum Type {TABLE, VIEW, QUERY, PROCEDURE, ALIAS, SYNONYM, MEMORY, GLOBAL_TEMPORARY, LOCAL_TEMPORARY, SYSTEM_TABLE, UNKNOWN}
	
	/**
	 * Type of dataset
	 */
	public Type getType () { sysParams.type as Type}
	/**
	 * Type of dataset
	 */
	public void setType(Type value) { sysParams.type = value }
	
	/**
	 * Database name
	 */
	public String getDbName () { ListUtils.NotNullValue([params.dbName, (connection as JDBCConnection).dbName]) }
	/**
	 * Database name
	 */
	public void setDbName (String value) { params.dbName = value }

	/**
	 * Schema name
	 */
	public String getSchemaName () { ListUtils.NotNullValue([params.schemaName, (connection as JDBCConnection).schemaName]) }
	/**
	 * Schema name
	 */
	public void setSchemaName (String value) { params.schemaName = value }

	/**
	 * Event on retrieve list of field 	
	 */
	public Closure getOnUpdateFields () { params.onUpdateFields }
	/**
	 * Event on retrieve list of field
	 */
	public void setOnUpdateFields (Closure value) { params.onUpdateFields = value }
	
	@Override
	public String getObjectName() { nameDataset() }
	
	@Override
	public String getObjectFullName() { fullNameDataset() }

	/**
	 * Name of dataset
	 */
	public String nameDataset () {
		JDBCDriver drv = connection?.driver as JDBCDriver
		(drv != null)?drv.nameDataset(this):getClass().name
	}

	/**
	 * Full name of dataset
	 */
	public String fullNameDataset () {
		JDBCDriver drv = connection?.driver as JDBCDriver
		(drv != null)?drv.fullNameDataset(this):getClass().name
	}
	
	@Override
	public void setConnection(Connection value) {
		assert value == null || value instanceof JDBCConnection
		super.setConnection(value)
	}
	
	/**
	 * Object name with SQL syntax
	 */
	public String sqlObjectName (String name) {
		GenerationUtils.SqlObjectName(this, name)
	}

	/**
	 * Objects name for SQL syntax
	 */
	public List<String> sqlListObjectName (List<String> listNames) {
		GenerationUtils.SqlListObjectName(this, listNames)
	}
	
	/**
	 * Return key fields name by sql syntax with expression and exclude fields list
	 * @param expr - string expression with {field} and {orig} macros
	 * @return - generated list
	 */
	public List<String> sqlKeyFields (String expr, List<String> excludeFields) {
		GenerationUtils.SqlKeyFields(this, field, expr, excludeFields)
	}
	
	/**
	 * Return key fields name by sql syntax
	 */
	public List<String> sqlKeyFields () {
		sqlKeyFields(null, null)
	}
	
	/**
	 * Return key fields name by sql syntax with expression and exclude fields list
	 * @param expr - string expression with {field} macros
	 * @return - generated list
	 */
	public List<String> sqlKeyFields (String expr) {
		sqlKeyFields(expr, null)
	}
	
	/**
	 * Return key fields name by sql syntax with expression and exclude fields list
	 */
	public List<String> sqlKeyFields (List<String> excludeFields) {
		sqlKeyFields(null, excludeFields)
	}
	
	/**
	 * Return fields name by sql syntax with expression and exclude fields list
	 * @param expr - string expression with {field} macros 
	 * @return - generated list
	 */
	public List<String> sqlFields (String expr, List<String> excludeFields) {
		GenerationUtils.SqlFields(this, field, expr, excludeFields)
	}
	
	/**
	 * Return fields name by sql syntax with expression and exclude fields list
	 */
	public List<String> sqlFieldsFrom (List<Field> fields, String expr) {
		GenerationUtils.SqlFields(this, fields, expr, null)
	}
	
	/**
	 * Return fields name by sql syntax
	 */
	public List<String> sqlFields () {
		sqlFields(null, null)
	}
	
	/**
	 * Return fields name by sql syntax with expression
	 * @param expr - string expression with {field} macros
	 * @return - generated list
	 */
	public List<String> sqlFields (String expr) {
		sqlFields(expr, null)
	}
	
	/**
	 * Return fields name by sql syntax with exclude fields list
	 */
	public List<String> sqlFields (List<String> excludeFields) {
		sqlFields(null, excludeFields)
	}

	boolean isExists() {
		if (!(connection.driver as JDBCDriver).isTable(this)) throw new ExceptionGETL("${fullNameDataset()} is not a table!")
		def con = connection as JDBCConnection
		def dbName = params.dbName
		def schemaName = params.schemaName
		def tableName = params.tableName
		if (tableName == null) throw new ExceptionGETL("Table name is not specified for ${fullNameDataset()}!")
		def ds = con.retrieveDatasets(dbName: dbName, schemaName: schemaName,
				tableName: tableName)

		return (!ds.isEmpty())
	}
}