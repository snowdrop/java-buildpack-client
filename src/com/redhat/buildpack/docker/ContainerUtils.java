package com.redhat.buildpack.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

public class ContainerUtils {

	public static String createContainer(DockerClient dc, String imageReference, VolumeBind... volumes) {
		return createContainer(dc, imageReference, null, volumes);
	}

	private static Bind createBind(VolumeBind vb) {
		return new Bind(vb.volumeName, new Volume(vb.mountPath));
	}

	public static String createContainer(DockerClient dc, String imageReference, List<String> command,
			VolumeBind... volumes) {

		CreateContainerCmd ccc = dc.createContainerCmd(imageReference);
		if (volumes != null) {
			List<Bind> binds = new ArrayList<>();
			for (VolumeBind vb : volumes) {
				Bind bind = createBind(vb);
				binds.add(bind);
			}
			ccc.getHostConfig().withBinds(binds);
		}

		//tbc, I think the buildpack container expects to run as root. At least it seems to execute things that require root
		//     eg, chmodding a mountpoint owned by root.
		ccc.withUser("root");

		//TODO: this a workaround for a bug in current buildpack 
		//      https://github.com/buildpacks/lifecycle/issues/339
		ccc.withEnv("CNB_PLATFORM_API=0.4", "CNB_REGISTRY_AUTH={}");
		
		if (command != null) {
			ccc.withCmd(command);
		}
		CreateContainerResponse ccr = ccc.exec();
		return ccr.getId();
	}

	public static void removeContainer(DockerClient dc, String containerId) {
		dc.removeContainerCmd(containerId).exec();
	}

	public static void addContentToContainer(DockerClient dc,
											 String containerId,
											 ContainerEntry... entries) throws IOException {
		addContentToContainer(dc, containerId, "", 0, 0, entries);
	}

	public static void addContentToContainer(DockerClient dc,
											 String containerId,
											 String pathInContainer,
											 Integer userId,
											 Integer groupId,
											 File content) throws IOException {
		addContentToContainer(dc, containerId, pathInContainer, userId, groupId, ContainerEntry.fromFile("",content));
	}

	public static void addContentToContainer(DockerClient dc,
											 String containerId,
											 String pathInContainer,
											 Integer userId,
											 Integer groupId,
											 String name,
											 String content) throws IOException {
		addContentToContainer(dc, containerId, pathInContainer, userId, groupId,
				ContainerEntry.fromString(name, content));
	}

	/**
	 * util method to add the parent dirs of a file/dir to an archive, to force them to exist with the 
	 * correct permissions, otherwise implicitly defined directories were being created with perms preventing the
	 * buildpack from executing as expected.
	 */
	private static void addParents(TarArchiveOutputStream tout, Set<String> seenDirs, int uid, int gid, String path) throws IOException {	
		if (path.contains("/")) {
			String parent = path.substring(0, path.lastIndexOf("/"));
			boolean unknown = seenDirs.add(parent);
			//only need to follow this chain if we haven't done it already =)
			if (unknown) {

				//add parents of this FIRST
				addParents(tout,seenDirs,uid,gid,parent);	

				//System.out.println("Adding entry '"+parent+"/'");
				//and then add this =)
				TarArchiveEntry tae = new TarArchiveEntry(parent+"/");
				tae.setSize(0);
				tae.setUserId(uid);
				tae.setGroupId(gid);
				tout.putArchiveEntry(tae);
				tout.closeArchiveEntry();
			}
		}
	}

	/**
	 * Adds content to the container, with specified uid/gid 
	 */
	public static void addContentToContainer(DockerClient dc,
											 String containerId,
											 String pathInContainer,
											 Integer userId,
											 Integer groupId,
											 ContainerEntry... entries) throws IOException {
		int uid = 0;
		int gid = 0;
		if (userId != null) {
			uid = userId;
		}
		if (groupId != null) {
			gid = groupId;
		}

		Set<String> seenDirs = new HashSet<>();
		//Don't add entry for "/", causes issues with tar format. 
		seenDirs.add("");

		if(!pathInContainer.isEmpty() && pathInContainer.endsWith("/")){
			pathInContainer = pathInContainer.substring(0,pathInContainer.length()-1);
		}

		//TODO: currently this is reading in the entire content into the PipedInputStream, before it 
		//      is fed back out into the TarArchiveOutputStream, this is bad, it consumes memory 
		//      at least equal to the content. It should be possible to have the for loop run in its
		//      own thread, and the piped writes will block until the piped inptu stream is read
		//      by the docker client. 
		PipedInputStream in = new PipedInputStream(1024 * 1024 * 500);
		PipedOutputStream out = new PipedOutputStream(in);
		TarArchiveOutputStream tout = new TarArchiveOutputStream(new GZIPOutputStream(new BufferedOutputStream(out)));
		tout.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
		for (ContainerEntry ve : entries) {

			String entryPath = ve.getPath();
			if(entryPath.startsWith("/")) entryPath = entryPath.substring(1);
			String path = pathInContainer+"/"+entryPath;

			addParents(tout,seenDirs,uid,gid,path);

			//System.out.println("Adding entry '"+ve.getPath()+"'");
			TarArchiveEntry tae = new TarArchiveEntry(path);
			tae.setSize(ve.getSize());
			tae.setUserId(uid);
			tae.setGroupId(gid);
			tout.putArchiveEntry(tae);
			InputStream is = ve.getContent();
			is.transferTo(tout);
			is.close();
			tout.closeArchiveEntry();
		}
		tout.close();

		//TODO: investigate if this is reading the entire stream into memory, or streaming it directly to the
		//docker api request. 
		dc.copyArchiveToContainerCmd(containerId).withRemotePath("/").withTarInputStream(in).exec();
	}
}
