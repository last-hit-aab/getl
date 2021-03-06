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

package getl.files

import getl.exception.ExceptionGETL
import getl.files.sub.FileManagerList
import getl.lang.sub.UserLogins
import getl.utils.FileUtils
import getl.utils.StringUtils
import groovy.transform.CompileStatic
import org.apache.hadoop.fs.FileStatus
import org.apache.hadoop.fs.FileSystem
import getl.utils.Logs
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.security.UserGroupInformation
import java.security.PrivilegedExceptionAction

/**
 * HDFS manager
 * @author Alexsey Konstantinov
 */
class HDFSManager extends Manager implements UserLogins {
    @Override
    void initParams() {
        super.initParams()
        params.storedLogins = [:] as Map<String, String>
    }

    @Override
    protected void initMethods() {
        super.initMethods()
        methodParams.register('super', ['server', 'port', 'login', 'storedLogins'])
    }

    /** Server address */
    String getServer() { params.server }
    /** Server address */
    void setServer(String value) { params.server = value }

    /** Server port */
    Integer getPort() { (params.port != null)?(params.port as Integer):8022 }
    /** Server port */
    void setPort(Integer value) { params.port = value }

    @Override
    String getLogin() { params.login }
    @Override
    void setLogin(String value) { params.login = value }

    @Override
    String getPassword() { params.password }
    @Override
    void setPassword(String value) { params.password = value }

    @Override
    Map<String, String> getStoredLogins() { params.storedLogins as Map<String, String> }
    @Override
    void setStoredLogins(Map<String, String> value) {
        storedLogins.clear()
        if (value != null) storedLogins.putAll(value)
    }

    /** File system driver */
    private FileSystem client

    /** Home directory by user */
    private String homeDirectory
    /** Home directory by user */
    String getHomeDirectory() { this.homeDirectory }

    @Override
    boolean isCaseSensitiveName() {
        return true
    }

    @Override
    boolean isConnected() { client != null }

    @Override
    void connect() {
        if (connected)
            throw new ExceptionGETL('Manager already connected!')

        if (server == null || port == null) throw new ExceptionGETL("Required server host and port for connect")
        if (login == null) throw new ExceptionGETL("Required login for connect")

        UserGroupInformation ugi = UserGroupInformation.createRemoteUser(login)
        ugi.doAs(
            new PrivilegedExceptionAction<Void>() {
                Void run() {
                    Configuration conf = new Configuration()
                    conf.set("fs.defaultFS", "hdfs://$server:$port")
                    conf.set("hadoop.job.ugi", login)

                    try {
                        client = FileSystem.get(conf)
                    }
                    catch (Exception e) {
                        if (writeErrorsToLog) Logs.Severe("Can not connect to $server:$port")
                        throw e
                    }
                    homeDirectory = client.homeDirectory
                    if (rootPath != null) currentPath = rootPath

                    return null
                }
            }
        )
    }

    @Override
    void disconnect() {
        if (!connected)
            throw new ExceptionGETL('Manager already disconnected!')

        try {
            if (client != null) client.close()
        }
        finally {
            client = null
            homeDirectory = null
            _currentPath = null
        }
    }

    @Override
    String getCurrentPath() {
        return _currentPath
    }

    @Override
    void setCurrentPath(String path) {
        validConnect()

        if (path == _currentPath) return

        if (path == null || path == '/') {
            if (writeErrorsToLog) Logs.Severe('Invalid path: \"$path\"')
            throw new ExceptionGETL('Invalid null path')
        }
        path = fullName(path, null)
        def p = new Path(path)
        if (!client.exists(p)) {
            if (writeErrorsToLog) Logs.Severe("Path \"$path\" not found")
            throw new ExceptionGETL("Path \"$path\" not found")
        }
        if (!client.exists(p) || !client.getFileStatus(p).isDirectory()) {
            if (writeErrorsToLog) Logs.Severe("Path \"$path\" non directory")
            throw new ExceptionGETL("Path \"$path\" non directory")
        }
        _currentPath = path
    }

    private fullName(String dir, String file) {
        if (dir != null && dir[0] == '/' && StringUtils.LeftStr(dir, 6) != '/user/') dir = dir.substring(1)
        if (dir == null) dir = currentPath
        if (!((dir + '/').matches(rootPath + '/.*'))) dir = rootPath + '/' + dir
        return ((dir != null)?dir:'') + ((file != null)?"/$file":'')
    }

    private Path fullPath(String dir, String file) {
        new Path(fullName(dir, file))
    }

    class HDFSList extends FileManagerList {
        FileStatus[] listFiles

        @CompileStatic
        @Override
        Integer size() {
            listFiles.length
        }

        @CompileStatic
        @Override
        Map item(int index) {
            FileStatus f = listFiles[index]

            Map<String, Object> m = new HashMap<String, Object>()
            m.filename = f.path.name
            m.filedate = new Date(f.modificationTime)
            m.filesize = f.len
            if  (f.isSymlink()) m.link = f.symlink.name

            if (f.directory) {
                m.type = Manager.TypeFile.DIRECTORY
            }
            else if (f.file) {
                m.type = Manager.TypeFile.FILE
            }
            else if (f.symlink) {
                m.type = Manager.TypeFile.LINK
            }
            else {
                throw new ExceptionGETL("Unnknown type object ${m.filename}")
            }

            m
        }

        @CompileStatic
        @Override
        void clear() {
            listFiles = null
        }
    }

    @Override
    FileManagerList listDir(String maskFiles) {
        validConnect()

        HDFSList res = new HDFSList()
        res.listFiles = client.listStatus(fullPath(_currentPath, null))

        return res
    }

    @Override
    void changeDirectoryUp() {
        validConnect()

        if (_currentPath == rootPath) {
            if (writeErrorsToLog) Logs.Severe("Can not change directory to up with root directory \"$rootPath\"")
            throw new ExceptionGETL("Can not change directory to up with root directory \"$rootPath\"")
        }

        String[] l = _currentPath.split('/')
        def n = []
        for (int i = 0; i < l.length - 1; i++) {
            n << l[i]
        }
        def c = n.join('/')

        try {
            currentPath = c
        }
        catch (Exception e) {
            if (writeErrorsToLog) Logs.Severe("Can not change directory to up: ${e.message}")
            throw e
        }
    }

    @Override
    void download(String fileName, String path, String localFileName) {
        validConnect()

        def fn = ((path != null)?path + '/':'') + localFileName
        try {
            def p = fullPath(_currentPath, fileName)
            client.copyToLocalFile(false, p, new Path(fn), true)
            def f = new File(fn)
            setLocalLastModified(f, getLastModified(fileName))
        }
        catch (Exception e) {
            if (writeErrorsToLog) Logs.Severe("Can not download file \"${fullName(_currentPath, fileName)}\" to \"$fn\"")
            throw e
        }
    }

    @Override
    void upload(String path, String fileName) {
        validConnect()

        def fn = ((path != null)?path + "/":"") + fileName
        try {
            def p = fullPath(_currentPath, fileName)
            client.copyFromLocalFile(new Path(fn), p)
            def f = new File(fn)
            setLastModified(fileName, f.lastModified())
        }
        catch (Exception e) {
            if (writeErrorsToLog) Logs.Severe("Can not upload file \"$fn\" to \"${fullName(_currentPath, fileName)}\"")
            throw e
        }
    }

    @Override
    void removeFile(String fileName) {
        validConnect()

        try {
            client.delete(fullPath(_currentPath, fileName), false)
        }
        catch (Exception e) {
            if (writeErrorsToLog) Logs.Severe("Can not remove file \"${fullName(_currentPath, fileName)}\"")
            throw e
        }
    }

    @Override
    void createDir(String dirName) {
        validConnect()

        try {
            client.mkdirs(fullPath(_currentPath, dirName))
        }
        catch (Exception e) {
            if (writeErrorsToLog) Logs.Severe("Can not create dir \"${fullName(_currentPath, dirName)}\"")
            throw e
        }
    }

    @Override
    void removeDir(String dirName, Boolean recursive) {
        validConnect()

        try {
            client.delete(fullPath(_currentPath, dirName), recursive)
        }
        catch (Exception e) {
            if (writeErrorsToLog) Logs.Severe("Can not remove dir \"${fullName(_currentPath, dirName)}\"")
            throw e
        }
    }

    @Override
    void rename(String fileName, String path) {
        validConnect()

        try {
            if (FileUtils.RelativePathFromFile(path, '/') == '.')
                path = fullPath(_currentPath, path)
            client.rename(fullPath(_currentPath, fileName), new Path(path))
        }
        catch (Exception e) {
            if (writeErrorsToLog) Logs.Severe("Can not rename file \"${fullName(_currentPath, fileName)}\" to \"$path\"")
            throw e
        }
    }

    @Override
    boolean existsDirectory(String dirName) {
        validConnect()

        def path = fullPath(dirName, null)
        def res = client.exists(path)
        if (res)
            res = client.getFileStatus(path).directory

        return res
    }

    @Override
    boolean existsFile(String fileName) {
        validConnect()

        def path = fullPath(null, fileName)
        def res = client.exists(path)
        if (res)
            res = client.getFileStatus(path).file

        return res
    }

    @Override
    long getLastModified(String fileName) {
        validConnect()

        def s = client.getFileStatus(fullPath(_currentPath, fileName))
        return s.modificationTime
    }

    @Override
    void setLastModified(String fileName, long time) {
        validConnect()

        if (saveOriginalDate)
            client.setTimes(fullPath(_currentPath, fileName), time, -1)
    }

    @Override
    String toString() {
        if (server == null) return 'hdfs'
        String res
        if (rootPath == null || rootPath.length() == 0)
            res = "hdfs://$server"
        else if (rootPath[0] == '/')
            res = "hdfs://$server$rootPath"
        else
            res = "hdfs://$server/$rootPath"

        return res
    }

    @Override
    void noop () {
        super.noop()
        client.getStatus()
    }

    @Override
    void useLogin(String user) {
        if (!storedLogins.containsKey(user))
            throw new ExceptionGETL("User \"$user\" not found in in configuration!")

        def pwd = storedLogins.get(user)

        def reconnect = (login != user && connected)
        if (reconnect) disconnect()
        login = user
        password = pwd
        if (reconnect) connect()
    }
}