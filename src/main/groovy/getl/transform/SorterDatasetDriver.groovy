/*
 GETL - based package in Groovy, which automates the work of loading and transforming data. His name is an acronym for "Groovy ETL".

 GETL is a set of libraries of pre-built classes and objects that can be used to solve problems unpacking,
 transform and load data into programs written in Groovy, or Java, as well as from any software that supports
 the work with Java classes.
 
 Copyright (C) EasyData Company LTD

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

package getl.transform

import getl.data.*
import getl.driver.VirtualDatasetDriver
import getl.exception.ExceptionGETL
import getl.utils.GenerationUtils
import groovy.transform.InheritConstructors

/**
 * Sorted driver class
 * @author Alexsey Konstantinov
 *
 */
@InheritConstructors
class SorterDatasetDriver extends VirtualDatasetDriver {
	private static List<String> getFieldOrderBy(Dataset dataset) {
		def res = dataset.params.fieldOrderBy as List<String>
		if (res == null) throw new ExceptionGETL("Required parameter \"fieldOrderBy\" in dataset")

		return res
	}
	
	@Override
	void openWrite(Dataset dataset, Map params, Closure prepareCode) {
		Dataset ds = getDestinition(dataset)
		def fieldOrderBy = getFieldOrderBy(dataset)
		
		ds.openWrite(params)
		if (prepareCode != null) prepareCode.call(dataset.field)
		
		dataset.driver_params.sorter_code = generateSortCode(fieldOrderBy)
		dataset.driver_params.sorter_data = new LinkedList<Map>()
	}

	@Override
	void write(Dataset dataset, Map row) {
		def data = dataset.driver_params.sorter_data as List<Map>
		data << row
	}

	@Override
	void doneWrite(Dataset dataset) {
		def data = dataset.driver_params.sorter_data as List<Map>
		def code = dataset.driver_params.sorter_code as Closure
		data.sort(true, code)
		
		Dataset ds = getDestinition(dataset)
		data.each { Map row ->
			ds.write(row)
		}
		ds.doneWrite()
	}

	@Override
	void closeWrite(Dataset dataset) {
		Dataset ds = getDestinition(dataset)
		ds.closeWrite()
		
		(dataset.driver_params.sorter_data as List).clear()
		dataset.driver_params.remove("sorter_data")
		dataset.driver_params.remove("sorter_code")
	}
	
	private static Closure generateSortCode(List<String> fieldOrderBy) {
		StringBuilder sb = new StringBuilder()
		sb << "{ Map row1, Map row2 ->\n	def eq\n"
		fieldOrderBy.each { field ->
			field = field.toLowerCase().replace("'", "\\'")

			sb << """
	eq = (row1.'$field' <=> row2.'$field')
	if (eq == 1) return 1
	if (eq == -1) return -1
"""
		}
		sb << "	return 0\n}"
		
		Closure result = GenerationUtils.EvalGroovyClosure(sb.toString())
		
		return result
	}
}
