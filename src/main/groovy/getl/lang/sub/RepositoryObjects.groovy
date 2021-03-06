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
package getl.lang.sub

import getl.exception.ExceptionDSL
import getl.lang.Getl
import getl.proc.sub.ExecutorThread
import getl.utils.BoolUtils
import getl.utils.Path
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository objects manager
 * @param <T> class of objects
 */
abstract class RepositoryObjects<T extends GetlRepository> implements GetlRepository {
    RepositoryObjects() {
        this.objects = new ConcurrentHashMap<String, T>()
    }

    /** Repository priority order */
    private Integer _priority
    /** Repository priority order */
    Integer getPriority() { _priority }
    /** Repository priority order */
    void setPriority(Integer value) { _priority = value }

    private String dslNameObject
    @Override
    String getDslNameObject() { dslNameObject }
    @Override
    void setDslNameObject(String value) { dslNameObject = value }

    private Getl dslCreator
    @Override
    Getl getDslCreator() { dslCreator }
    @Override
    void setDslCreator(Getl value) { dslCreator = value }

    @Override
    void dslCleanProps() {
        dslNameObject = null
        dslCreator = null
    }

    /** Repository objects */
    private Map<String, T> objects
    /** Repository objects */
    Map<String, T> getObjects() { objects }
    /** Repository objects */
    void setObjects(Map<String, T> value) {
        synchronized (this) {
            this.objects = value
        }
    }

    /** List of supported classes for objects */
    abstract List<String> getListClasses()

    /**
     * Return list of repository objects for specified mask, classes and filter
     * @param mask filter mask (use Path expression syntax)
     * @param classes list of required classes to search
     * @param filter object filtering code
     * @return list of names repository objects according to specified conditions
     */
    @SuppressWarnings("GroovySynchronizationOnNonFinalField")
    List<String> list(String mask = null, List<String> classes = null,
                      @ClosureParams(value = SimpleType, options = ['java.lang.String', 'java.lang.Object'])
                            Closure<Boolean> filter = null) {
        (classes as List<String>)?.each {
            if (!(it in listClasses))
                throw new ExceptionDSL("\"$it\" is not a supported $typeObject class!")
        }

        def res = [] as List<String>

        def masknames = dslCreator.parseName(mask)
        def maskgroup = masknames.groupName?:dslCreator.filteringGroup
        def maskobject = masknames.objectName
        def grouppath = (maskgroup != null)?new Path(mask: maskgroup):null
        def objectpath = (maskobject != null)?new Path(mask: maskobject):null

        synchronized (objects) {
            objects.each { name, obj ->
                def names = new ParseObjectName(name)
                if (grouppath != null) {
                    if (grouppath.match(names.groupName))
                        if (objectpath == null || objectpath.match(names.objectName))
                            if (classes == null || obj.getClass().name in classes)
                                if (filter == null || BoolUtils.IsValue(filter.call(name, obj))) res << name
                } else {
                    if (objectpath == null || (names.groupName == null && objectpath.match(names.objectName)))
                        if (classes == null || obj.getClass().name in classes)
                            if (filter == null || BoolUtils.IsValue(filter.call(name, obj))) res << name
                }
            }
        }

        return res
    }

    /**
     * Search for an object in the repository
     * @param obj object
     * @return name of the object in the repository or null if not found
     */
    String find(T obj) {
        def repName = obj.dslNameObject
        if (repName == null) return null

        def className = obj.getClass().name
        if (!(className in listClasses)) return null

        def repObj = objects.get(dslCreator.repObjectName(repName))
        if (repObj == null) return null
        if (repObj.getClass().name != className) return null

        return repName
    }

    /**
     * Find a object by name
     * @param name repository name
     * @return found object or null if not found
     */
    T find(String name) {
        return objects.get(dslCreator.repObjectName(name))
    }

    /**
     * Initialize registered object
     * @param obj object registered object
     */
    protected void initRegisteredObject(T obj) { }

    /**
     * Register object in repository
     * @param obj object for registration
     * @param name name object in repository
     * @param validExist checking if an object is registered in the repository (default true)
     */
    @SuppressWarnings("GroovySynchronizationOnNonFinalField")
    T registerObject(Getl creator, T obj, String name = null, Boolean validExist = true) {
        if (obj == null)
            throw new ExceptionDSL("$typeObject cannot be null!")

        def className = obj.getClass().name
        if (!(className in listClasses))
            throw new ExceptionDSL("Unknown $typeObject class $className!")

        if (name == null) {
            obj.dslCreator = creator
            return obj
        }

        validExist = BoolUtils.IsValue(validExist, true)
        def repName = dslCreator.repObjectName(name, true)

        synchronized (objects) {
            if (validExist) {
                def exObj = objects.get(repName)
                if (exObj != null)
                    throw new ExceptionDSL("\"$name\" already registered for class \"${exObj.getClass().name}\" in \"$typeObject\" repository!")
            }

            obj.dslNameObject = repName
            obj.dslCreator = (repName[0] == '#')?creator:dslCreator

            objects.put(repName, obj)
            initRegisteredObject(obj)
        }

        return obj
    }

    /**
     * Create new object by specified class
     * @param className class name
     * @return new object instance
     */
    abstract protected T createObject(String className)

    /**
     * Clone object
     * @param object cloned object
     * @return new instance object
     */
    protected T cloneObject(T object) {
        object.clone() as T
    }

    /** The name of the collection for storing cloned objects for threads */
    String getNameCloneCollection() { this.class.name }

    /** Type of repository object  */
    String getTypeObject() {  this.class.simpleName }

    /**
     * Process register object
     * @param className class object
     * @param name repository name
     * @param registration need registration
     * @param repObj repository object, when object cloned in thread
     * @param cloneObj cloned object
     * @param params extended parameters
     */
    protected void processRegisterObject(Getl creator, String className, String name, Boolean registration, GetlRepository repObj,
                                         GetlRepository cloneObj, Map params) { }

    /**
     * Register an object by name or return an existing one
     * @param className object class
     * @param name object name
     * @param registration register a new object or return an existing one
     */
    @SuppressWarnings("GroovySynchronizationOnNonFinalField")
    T register(Getl creator, String className, String name = null, Boolean registration = false, Map params = null) {
        registration = BoolUtils.IsValue(registration)

        if (className == null && registration)
            throw new ExceptionDSL('Class name cannot be null!')

        if (className != null && !(className in listClasses))
            throw new ExceptionDSL("$className class is not supported by the ${typeObject}s repository!")

        if (name == null) {
            def obj = createObject(className)
            obj.dslCreator = creator
            processRegisterObject(creator, className, name, registration, obj, null, params)
            return obj
        }

        def isThread = (dslCreator.options().useThreadModelConnection && Thread.currentThread() instanceof ExecutorThread)

        def repName = dslCreator.repObjectName(name, true)
        if (!registration && isThread) {
            def thread = Thread.currentThread() as ExecutorThread
            def threadobj = thread.findDslCloneObject(nameCloneCollection, repName) as T
            if (threadobj != null)
                return threadobj
        }

        T obj
        synchronized (objects) {
            obj = objects.get(repName)

            if (obj == null) {
                if (registration && isThread)
                    throw new ExceptionDSL("it is not allowed to register an \"$name\" $typeObject inside a thread!")

                if (!registration && dslCreator.options().validRegisterObjects)
                    throw new ExceptionDSL("$typeObject \"$name\" is not registered!")

                obj = createObject(className)
                obj.dslNameObject = repName
                obj.dslCreator = (repName[0] == '#')?creator:dslCreator
                objects.put(repName, obj)
                initRegisteredObject(obj)
            } else {
                if (registration)
                    throw new ExceptionDSL("$typeObject \"$name\" already registered for class \"${obj.getClass().name}\"!")
                else {
                    if (className != null && obj.getClass().name != className)
                        throw new ExceptionDSL("The requested $typeObject \"$name\" of the class \"$className\" is already registered for the class \"${obj.getClass().name}\"!")
                }
            }
        }

        if (isThread) {
            def thread = Thread.currentThread() as ExecutorThread
            def threadobj = thread.registerCloneObject(nameCloneCollection, obj,
                    {
                        def par = it as T
                        def c = cloneObject(par)
                        c.dslNameObject = repName
                        c.dslCreator = par.dslCreator
                        return c
                    }
            ) as T

            processRegisterObject(creator, className, name, registration, obj, threadobj, params)
            obj = threadobj
        }
        else {
            processRegisterObject(creator, className, name, registration, obj, null, params)
        }

        return obj
    }

    /**
     * Unregister objects by a given mask or a list of their classes
     * @param mask mask of objects (in Path format)
     * @param classes list of processed classes
     * @param filter filter for detect objects to unregister
     */
    void unregister(String mask = null, List<String> classes = null,
                              @ClosureParams(value = SimpleType, options = ['java.lang.String', 'java.lang.Object'])
                                      Closure<Boolean> filter = null) {
        def list = list(mask, classes, filter)
        list.each { name ->
            objects.remove(name)?.dslCleanProps()
        }
    }

    /**
     * Release temporary object
     * @param creator object creator
     */
    void releaseTemporary(Getl creator = null) {
        def list = list('#*')
        list.each { name ->
            def obj = objects.get(name)
            if (creator == null || obj.dslCreator == creator)
                objects.remove(name)?.dslCleanProps()
        }
    }

    /**
     * Process repository objects for specified mask and class
     * @param mask filter mask (use Path expression syntax)
     * @param classes list of need classes
     * @param cl processing code
     */
    void processObjects(String mask, List<String> classes,
                        @ClosureParams(value = SimpleType, options = ['java.lang.String']) Closure cl) {
        if (cl == null)
            throw new ExceptionDSL('Process required closure code!')

        def list = list(mask, classes)
        list.each { name ->
            cl.call(name)
        }
    }

    /**
     * Process repository objects for specified mask and class
     * @param mask filter mask (use Path expression syntax)
     * @param classes list of need classes
     * @param cl processing code
     */
    void processObjects(String mask,
                        @ClosureParams(value = SimpleType, options = ['java.lang.String']) Closure cl) {
        processObjects(mask, null, cl)
    }

    /**
     * Object parameters for export
     * @param obj exported object
     * @return configuration object
     */
    abstract Map exportConfig(GetlRepository obj)

    /**
     * Import object from parameters
     * @param name the name of the object in the repository
     * @param params object parameters
     * @return registered object
     */
    abstract GetlRepository importConfig(Map config)

    /** Repository objects require configuration storage separately for different environments */
    boolean needEnvConfig() { false }
}