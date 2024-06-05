package dev.snowdrop.buildpack.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class FilePermissions {
    public Integer getPermissions(File file){
          try{
            Set<PosixFilePermission> fp = Files.getPosixFilePermissions(file.toPath());
            int mode = (fp.contains(PosixFilePermission.OWNER_READ)?0400:0) + (fp.contains(PosixFilePermission.OWNER_WRITE)?0200:0) + (fp.contains(PosixFilePermission.OWNER_EXECUTE)?0100:0) +
                       (fp.contains(PosixFilePermission.GROUP_READ)?040:0) + (fp.contains(PosixFilePermission.GROUP_WRITE)?020:0) + (fp.contains(PosixFilePermission.GROUP_EXECUTE)?010:0) +
                       (fp.contains(PosixFilePermission.OTHERS_READ)?04:0) + (fp.contains(PosixFilePermission.OTHERS_WRITE)?02:0) + (fp.contains(PosixFilePermission.OTHERS_EXECUTE)?01:0);
            return mode;
          }catch(IOException io){
            //may not be able to process posixfileperms on all platforms, fall back to java io File perms, and set as owner & group
            return ( (file.canRead()?0400:0) + (file.canWrite()?0200:0) + (file.canExecute()?0100:0) )+
                   ( (file.canRead()?040:0) + (file.canWrite()?020:0) + (file.canExecute()?010:0) );                   
          }
    }
}
