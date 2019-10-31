/*
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

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import org.pipeline.obs.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;

public class WithOBSStep extends Step {

	private String role = "";
	private String roleAccount = "";
	private String region = "";
	private String endpointUrl = "";
	private String profile = "";
	private String credentials = "";
	private String externalId = "";
	private String federatedUserId = "";
	private String policy = "";
	private String iamMfaToken = "";
	private Integer duration = 3600;
	private String roleSessionName;
	private String principalArn = "";
	private String samlAssertion = "";

	@DataBoundConstructor
	public WithOBSStep() {
		//
	}

	public String getRole() {
		return this.role;
	}

	@DataBoundSetter
	public void setRole(String role) {
		this.role = role;
	}

	public String getRoleAccount() {
		return this.roleAccount;
	}

	@DataBoundSetter
	public void setRoleAccount(String roleAccount) {
		this.roleAccount = roleAccount;
	}

	public String getRegion() {
		return this.region;
	}

	@DataBoundSetter
	public void setRegion(String region) {
		this.region = region;
	}

	public String getEndpointUrl() {
		return this.endpointUrl;
	}

	@DataBoundSetter
	public void setEndpointUrl(String endpointUrl) {
		this.endpointUrl = endpointUrl;
	}

	public String getProfile() {
		return this.profile;
	}

	@DataBoundSetter
	public void setProfile(String profile) {
		this.profile = profile;
	}

	public String getCredentials() {
		return this.credentials;
	}

	@DataBoundSetter
	public void setCredentials(String credentials) {
		this.credentials = credentials;
	}

	public String getExternalId() {
		return this.externalId;
	}

	@DataBoundSetter
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getIamMfaToken() {
		return this.iamMfaToken;
	}

	@DataBoundSetter
	public void setIamMfaToken(String iamMfaToken) {
		this.iamMfaToken = iamMfaToken;
	}

	public String getFederatedUserId() {
		return this.federatedUserId;
	}

	@DataBoundSetter
	public void setFederatedUserId(String federatedUserId) {
		this.federatedUserId = federatedUserId;
	}

	public String getPolicy() {
		return this.policy;
	}

	@DataBoundSetter
	public void setPolicy(String policy) {
		this.policy = policy;
	}

	public Integer getDuration() {
		return this.duration;
	}

	@DataBoundSetter
	public void setDuration(Integer duration) {
		this.duration = duration;
	}

	public String getRoleSessionName() {
		return this.roleSessionName;
	}

	@DataBoundSetter
	public void setRoleSessionName(String roleSessionName) {
		this.roleSessionName = roleSessionName;
	}

	public String getPrincipalArn() {
		return this.principalArn;
	}

	@DataBoundSetter
	public void setPrincipalArn(final String principalArn) {
		this.principalArn = principalArn;
	}

	public String getSamlAssertion() {
		return this.samlAssertion;
	}

	@DataBoundSetter
	public void setSamlAssertion(final String samlAssertion) {
		this.samlAssertion = samlAssertion;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new WithOBSStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, Run.class);
		}

		@Override
		public String getFunctionName() {
			return "withOBS";
		}

		@Override
		public String getDisplayName() {
			return "set OBS settings for nested block";
		}

		@Override
		public boolean takesImplicitBlockArgument() {
			return true;
		}

		public ListBoxModel doFillCredentialsItems(@AncestorInPath Item context) {

			if (context == null || !context.hasPermission(Item.CONFIGURE)) {
				return new ListBoxModel();
			}

			return new StandardListBoxModel()
					.includeEmptyValue()
					.includeMatchingAs(
							context instanceof Queue.Task
									? Tasks.getAuthenticationOf((Queue.Task) context)
									: ACL.SYSTEM,
							context,
							StandardUsernamePasswordCredentials.class,
							Collections.emptyList(),
							CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
		}
	}

	public static class Execution extends StepExecution {

		private final transient WithOBSStep step;

		private final EnvVars envVars;

		public Execution(WithOBSStep step, StepContext context) {
			super(context);
			this.step = step;
			try {
				this.envVars = context.get(EnvVars.class);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public boolean start() throws Exception {
			final EnvVars obsEnv = new EnvVars();
			obsEnv.override(OBSClientFactory.OBS_DEFAULT_REGION, this.step.getRegion());
			obsEnv.override(OBSClientFactory.OBS_REGION, this.step.getRegion());


			StandardUsernamePasswordCredentials usernamePasswordCredentials = CredentialsProvider.findCredentialById(this.step.getCredentials(),
				StandardUsernamePasswordCredentials.class, this.getContext().get(Run.class), Collections.emptyList());

			if (usernamePasswordCredentials != null) {
				obsEnv.override(OBSClientFactory.OBS_ACCESS_KEY_ID, usernamePasswordCredentials.getUsername());
				obsEnv.override(OBSClientFactory.OBS_SECRET_ACCESS_KEY, usernamePasswordCredentials.getPassword().getPlainText());
			}

			obsEnv.override(OBSClientFactory.OBS_ENDPOINT_URL, this.step.getEndpointUrl());

			EnvironmentExpander expander = new EnvironmentExpander() {
				@Override
				public void expand(@Nonnull EnvVars envVars) {
					envVars.overrideAll(obsEnv);
				}
			};
			this.getContext().newBodyInvoker()
					.withContext(EnvironmentExpander.merge(this.getContext().get(EnvironmentExpander.class), expander))
					.withCallback(BodyExecutionCallback.wrap(this.getContext()))
					.start();
			return false;
		}

		private static final String ALLOW_ALL_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":\"*\","
				+ "\"Effect\":\"Allow\",\"Resource\":\"*\"}]}";

		private static final long serialVersionUID = 1L;

	}

}
