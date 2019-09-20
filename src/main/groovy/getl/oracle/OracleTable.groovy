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

package getl.oracle

import getl.data.Connection
import getl.exception.ExceptionGETL
import getl.jdbc.*
import getl.jdbc.opts.ReadSpec
import getl.oracle.opts.OracleReadSpec
import groovy.transform.InheritConstructors
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

/**
 * Oracle table
 * @author Alexsey Konstantinov
 *
 */
@InheritConstructors
class OracleTable extends InternalTableDataset {
    @Override
    void setConnection(Connection value) {
        if (value != null && !(value instanceof OracleConnection))
            throw new ExceptionGETL('Сonnection to OracleConnection class is allowed!')

        super.setConnection(value)
    }

    /** Use specified connection */
    OracleConnection useConnection(OracleConnection value) {
        setConnection(value)
        return value
    }

    @Override
    protected ReadSpec newReadTableParams(def ownerObject, def thisObject, Boolean useExternalParams,
                                          Map<String, Object> opts) {
        new OracleReadSpec(ownerObject, thisObject, useExternalParams, opts)
    }

    /**
     * Read table options
     */
    OracleReadSpec readOpts(@DelegatesTo(OracleReadSpec)
                            @ClosureParams(value = SimpleType, options = ['getl.oracle.opts.OracleReadSpec'])
                                    Closure cl = null) {
        genReadDirective(cl) as OracleReadSpec
    }

    /**
     * Perform operations on a table
     * @param cl closure code
     * @return source table
     */
    OracleTable dois(@DelegatesTo(OracleTable) Closure cl) {
        this.with(cl)
        return this
    }
}