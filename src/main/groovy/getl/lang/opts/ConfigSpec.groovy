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

package getl.lang.opts

import getl.config.*
import getl.exception.ExceptionDSL
import getl.lang.Getl
import getl.utils.*
import groovy.transform.InheritConstructors

/**
 * Config specification class
 * @author Alexsey Konstantinov
 *
 */
@InheritConstructors
class ConfigSpec extends BaseSpec {
    /**
     * Configuration manager
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    ConfigSlurper getManager() { Config.configClassManager as ConfigSlurper }

    /**
     * Configuration files path
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    String getPath() { manager.path }
    /**
     * Configuration files path
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    void setPath(String value) { manager.path = value }

    /**
     * Code page in configuration files
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    String getCodePage() { manager.codePage }
    /**
     * Code page in configuration files
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    void setCodePage(String value) { manager.codePage = value }

    /**
     * Load configuration file
     * @param fileName configuration file name
     * @param environment environment
     * @param codePage code page file
     */
    void load(String fileName, String environment = null, String codePage = null) {
        Config.LoadConfig([fileName: FileUtils.ResourceFileName(fileName), codePage: codePage, environment: environment])
    }

    /**
     * Load configuration file
     * @param fileName configuration file name
     * @param environment environment
     * @param secretKey encode key string
     */
    void loadEncrypt(String fileName, String environment = null, String secretKey = null) {
        def data = ConfigStores.LoadSection(FileUtils.ResourceFileName(fileName), secretKey, environment?:manager.environment?:'all')
        Config.MergeConfig(data)
    }

    /**
     * Save configuration file
     * @param fileName configuration file name
     * @param codePage code page file
     */
    void save(String fileName, String codePage = null) {
        Config.SaveConfig(fileName: fileName, codePage: codePage)
    }

    /**
     * Save configuration file
     * @param data saved data
     * @param fileName configuration file name
     * @param environment environment
     * @param secretKey encode key string
     */
    void saveEncrypt(Map data, String fileName, String environment = null, String secretKey = null) {
        ConfigStores.SaveSection(data, fileName, secretKey, environment?:manager.environment?:'all')
    }

    /**
     * Clear configuration content
     */
    void clear() { Config.ClearConfig() }

    /**
     * read fields from the specified configuration section
     * @param section path to store variables in configuration
     * @param validExist check for the existence of fields in the script
     */
    void readFields(String section, Boolean validExist = true) {
        def vars = Config.FindSection(section)
        if (vars == null)
            throw new ExceptionDSL("Configuration section \"$section\" not found!")
        if (vars.isEmpty())
            return

        Getl.FillFieldFromVars(ownerObject as Script, vars, validExist)
    }

    /** Current environment */
    @SuppressWarnings("GrMethodMayBeStatic")
    String getEnvironment() {
        return manager.environment
    }

    /** Current environment */
    @SuppressWarnings("GrMethodMayBeStatic")
    void setEnvironment(String value) {
        manager.environment = value
    }
}