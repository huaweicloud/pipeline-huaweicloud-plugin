/*
 * -
 * #%L
 * Pipeline: OBS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.pipeline.obs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.Charset;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Preconditions;
import com.obs.services.ObsClient;
import com.huawei.openstack4j.openstack.fgs.v2.domain.FunctionMetadata;
import com.huawei.openstack4j.api.OSClient;
import com.huawei.openstack4j.api.types.ServiceType;
import com.huawei.openstack4j.core.transport.Config;
import com.huawei.openstack4j.model.common.Identifier;
import com.huawei.openstack4j.openstack.OSFactory;

import com.huawei.openstack4j.api.OSClient.OSClientAKSK;
import com.huawei.openstack4j.model.identity.v3.User;
import com.huawei.openstack4j.openstack.fgs.v2.domain.FuncInvocations;

import org.pipeline.obs.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

public class OBSUploadStep extends Step {

	private final String bucket;
	private ObsClient obs;
	private String file;
	private String text;
	private String path = "";
	private String kmsId;
	private String includePathPattern;
	private String excludePathPattern;
	private String workingDir;
	private String[] metadatas;
	private String cacheControl;
	private String contentEncoding;
	private String contentType;
	private String sseAlgorithm;
	private String redirectLocation;
	private boolean verbose = true;

	@DataBoundConstructor
	public OBSUploadStep(String bucket) {
		this.bucket = bucket;
	}

	public String getFile() {
		return this.file;
	}

	@DataBoundSetter
	public void setFile(String file) {
		this.file = file;
	}

	public String getText() {
		return this.text;
	}

	@DataBoundSetter
	public void setText(String text) {
		this.text = text;
	}

	public String getBucket() {
		return this.bucket;
	}

	public String getPath() {
		return this.path;
	}

	public String getKmsId() {
		return this.kmsId;
	}

	@DataBoundSetter
	public void setKmsId(String kmsId) {
		this.kmsId = kmsId;
	}

	@DataBoundSetter
	public void setPath(String path) {
		this.path = path;
	}

	public String getIncludePathPattern() {
		return this.includePathPattern;
	}

	@DataBoundSetter
	public void setIncludePathPattern(String includePathPattern) {
		this.includePathPattern = includePathPattern;
	}

	public String getExcludePathPattern() {
		return this.excludePathPattern;
	}

	@DataBoundSetter
	public void setExcludePathPattern(String excludePathPattern) {
		this.excludePathPattern = excludePathPattern;
	}

	public String getWorkingDir() {
		return this.workingDir;
	}

	@DataBoundSetter
	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}

	public String getRedirectLocation() {
		return this.redirectLocation;
	}

	@DataBoundSetter
	public void setRedirectLocation(String redirectLocation) {
		this.redirectLocation = redirectLocation;
	}

	public String[] getMetadatas() {
		if (this.metadatas != null) {
			return this.metadatas.clone();
		} else {
			return null;
		}
	}

	@DataBoundSetter
	public void setMetadatas(String[] metadatas) {
		if (metadatas != null) {
			this.metadatas = metadatas.clone();
		} else {
			this.metadatas = null;
		}
	}

	public String getCacheControl() {
		return this.cacheControl;
	}

	@DataBoundSetter
	public void setCacheControl(final String cacheControl) {
		this.cacheControl = cacheControl;
	}

	public String getContentEncoding() {
		return this.contentEncoding;
	}

	@DataBoundSetter
	public String setContentEncoding(final String contentEncoding) {
		return this.contentEncoding = contentEncoding;
	}

	public String getContentType() {
		return this.contentType;
	}

	@DataBoundSetter
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getSseAlgorithm() {
		return this.sseAlgorithm;
	}

	@DataBoundSetter
	public void setSseAlgorithm(String sseAlgorithm) {
		this.sseAlgorithm = sseAlgorithm;
	}

	@DataBoundSetter
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerbose() {
		return this.verbose;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new OBSUploadStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "obsUpload";
		}

		@Override
		public String getDisplayName() {
			return "Copy file to obs";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<String> {

		protected static final long serialVersionUID = 1L;

		protected final transient OBSUploadStep step;

		public Execution(OBSUploadStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public String run() throws Exception {
			final String file = this.step.getFile();
			final String text = this.step.getText();
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final String kmsId = this.step.getKmsId();
			final String includePathPattern = this.step.getIncludePathPattern();
			final String excludePathPattern = this.step.getExcludePathPattern();
			final String workingDir = this.step.getWorkingDir();
			final Map<String, String> metadatas = new HashMap<>();
			final String cacheControl = this.step.getCacheControl();
			final String contentEncoding = this.step.getContentEncoding();
			final String contentType = this.step.getContentType();
			final String sseAlgorithm = this.step.getSseAlgorithm();
			final String redirectLocation = this.step.getRedirectLocation();
			final boolean verbose = this.step.getVerbose();
			boolean omitSourcePath = false;
			boolean sendingText = false;
			String localPath = null;

			if (this.step.getMetadatas() != null && this.step.getMetadatas().length != 0) {
				for (String metadata : this.step.getMetadatas()) {
					if (metadata.contains(":")) {
						metadatas.put(metadata.substring(0, metadata.indexOf(':')), metadata.substring(metadata.indexOf(':') + 1));
					}
				}
			}

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(file != null || includePathPattern != null, "File or IncludePathPattern must not be null");
			Preconditions.checkArgument(includePathPattern == null || file == null, "File and IncludePathPattern cannot be use together");

			final List<FilePath> children = new ArrayList<>();
			final FilePath dir;
			if (workingDir != null && !"".equals(workingDir.trim())) {
				dir = this.getContext().get(FilePath.class).child(workingDir);
			} else {
				dir = this.getContext().get(FilePath.class);
			}
			if (text != null) {
				sendingText = true;
			} else if (file != null) {
				children.add(dir.child(file));
				omitSourcePath = true;
			} else if (excludePathPattern != null && !excludePathPattern.trim().isEmpty()) {
				children.addAll(Arrays.asList(dir.list(includePathPattern, excludePathPattern, true)));
			} else {
				children.addAll(Arrays.asList(dir.list(includePathPattern, null, true)));

			}

			TaskListener listener = Execution.this.getContext().get(TaskListener.class);

			if (sendingText) {
				return String.format("obs://%s/%s", bucket, localPath);
			} else if (children.isEmpty()) {
				listener.getLogger().println("Nothing to upload");
				return null;
			} else if (omitSourcePath) {
				FilePath child = children.get(0);
				listener.getLogger().format("Uploading %s to obs://%s/%s %n",
					child.toURI(), bucket, path);
				if (!child.exists()) {
					listener.getLogger().println("Upload failed due to missing source file");
					throw new FileNotFoundException(child.toURI().toString());
				}

				child.act(new RemoteUploader(Execution.this.getContext().get(EnvVars.class), listener, bucket, path, metadatas, cacheControl, contentEncoding, contentType, kmsId, sseAlgorithm, redirectLocation));

				listener.getLogger().println("Upload complete");
				return String.format("obs://%s/%s", bucket, path);
			} else {
				List<File> fileList = new ArrayList<>();
				listener.getLogger().format("Uploading %s to obs://%s/%s %n",
					includePathPattern, bucket, path);
				for (FilePath child : children) {
					fileList.add(child.act(FIND_FILE_ON_SLAVE));
				}
				dir.act(new RemoteListUploader(Execution.this.getContext().get(EnvVars.class), listener, fileList, bucket, path, metadatas, cacheControl, contentEncoding, contentType, kmsId, sseAlgorithm));
				listener.getLogger().println("Upload complete");
				return String.format("obs://%s/%s", bucket, path);
			}
		}

	}

	private static class RemoteUploader extends MasterToSlaveFileCallable<Void> {

		protected static final long serialVersionUID = 1L;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final Map<String, String> metadatas;
		private final String cacheControl;
		private final String contentEncoding;
		private final String contentType;
		private final String kmsId;
		private final String sseAlgorithm;
		private final String redirectLocation;

		RemoteUploader(EnvVars envVars, TaskListener taskListener, String bucket, String path, Map<String, String> metadatas, String cacheControl, String contentEncoding, String contentType, String kmsId, String sseAlgorithm, String redirectLocation) {
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
			this.metadatas = metadatas;
			this.cacheControl = cacheControl;
			this.contentEncoding = contentEncoding;
			this.contentType = contentType;
			this.kmsId = kmsId;
			this.sseAlgorithm = sseAlgorithm;
			this.redirectLocation = redirectLocation;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			ObsClient obs =
				OBSClientFactory.createHuaweiObsClient(this.envVars);

				if (localFile.isFile()) {
					String path = this.path;
					if (path.endsWith("/") || path.isEmpty()) {
						path += localFile.getName();
					}
					if (!obs.headBucket(this.bucket)) {
						throw new FileNotFoundException("Bucket "
							+ this.bucket + " does not exist");
					}

					obs.putObject(this.bucket, path, localFile);

				return null;
			}

			return null;
		}
	}

	private static class RemoteListUploader extends MasterToSlaveFileCallable<Void> {

		protected static final long serialVersionUID = 1L;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final List<File> fileList;
		private final Map<String, String> metadatas;
		private final String cacheControl;
		private final String contentEncoding;
		private final String contentType;
		private final String kmsId;
		private final String sseAlgorithm;

		RemoteListUploader(EnvVars envVars, TaskListener taskListener, List<File> fileList, String bucket, String path, Map<String, String> metadatas, final String cacheControl, final String contentEncoding, final String contentType, String kmsId, String sseAlgorithm) {
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.fileList = fileList;
			this.bucket = bucket;
			this.path = path;
			this.metadatas = metadatas;
			this.cacheControl = cacheControl;
			this.contentEncoding = contentEncoding;
			this.contentType = contentType;
			this.kmsId = kmsId;
			this.sseAlgorithm = sseAlgorithm;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			return null;
		}
	}

	private static MasterToSlaveFileCallable<File> FIND_FILE_ON_SLAVE = new MasterToSlaveFileCallable<File>() {
		@Override
		public File invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			return localFile;
		}
	};

}