/*
 * -
 * #%L
 * Pipeline: OBS Steps
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

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.huawei.openstack4j.api.OSClient.OSClientAKSK;
import com.huawei.openstack4j.openstack.fgs.v2.domain.FunctionMetadata;
import com.huawei.openstack4j.openstack.fgs.v2.domain.FuncInvocations;
import com.huawei.openstack4j.openstack.fgs.v2.internal.FunctionV2Service;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import org.pipeline.obs.utils.JsonUtils;
import org.pipeline.obs.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class InvokeFunctionStep extends Step {

	private Object payload;
	private String payloadAsString;
	private boolean returnValueAsString = false;
	private final String functionName;

	@DataBoundConstructor
	public InvokeFunctionStep(String functionName) {
		this.functionName = functionName;
	}

	public String getFunctionName() {
		return this.functionName;
	}

	public Object getPayload() {
		return this.payload;
	}

	@DataBoundSetter
	public void setPayload(Object payload) {
		this.payload = payload;
	}

	public String getPayloadAsString() {
		if (this.payload != null) {
			return JsonUtils.toString(this.payload);
		}
		return this.payloadAsString;
	}

	@DataBoundSetter
	public void setPayloadAsString(String payloadAsString) {
		this.payloadAsString = payloadAsString;
	}

	public boolean isReturnValueAsString() {
		return this.returnValueAsString;
	}

	@DataBoundSetter
	public void setReturnValueAsString(boolean returnValueAsString) {
		this.returnValueAsString = returnValueAsString;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new InvokeFunctionStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "invokeFunction";
		}

		@Override
		public String getDisplayName() {
			return "Invoke a given function";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Object> {

		private static final long serialVersionUID = 1L;

		private final transient InvokeFunctionStep step;

		public Execution(InvokeFunctionStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Object run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			OSClientAKSK osclient =
				OBSClientFactory.createOscClient(this.getContext());
			String functionName = this.step.getFunctionName();
			listener.getLogger().format("Invoke function %s%n", functionName);

			String functionUrn = "";

			FunctionMetadata.Functions functions = osclient.functionGraphV2().function().listFunction();
			List<FunctionMetadata> functionList = functions.getList();
			if (0 != functionList.size()) {
				for (FunctionMetadata fd : functionList) {
					if (functionName.equals(fd.getFuncName())) {
						functionUrn = fd.getFuncUrn();
						break;
					}
				}
				listener.getLogger().format("Get functionlist success " +
					"functionUrn=%s!", functionUrn);
			}else {
				listener.getLogger().format("Get functionlist failed!");
			}

			if (functionUrn == "") {
				throw new RuntimeException("Invoke function failed! " +
					"functionName=" + functionName);
			}

			Map<String, String> map = new HashMap<String, String>();
			ObjectMapper mapper = new ObjectMapper();

			try{
				map = mapper.readValue(this.step.getPayloadAsString(), new TypeReference<HashMap<String,
					String>>(){});
			}catch(Exception e){
				e.printStackTrace();
			}

			//Synchronous execution function
			FuncInvocations returnMsg =
				osclient.functionGraphV2().function().invokeFunction(functionUrn,
					map);
			if (null != returnMsg) {
				listener.getLogger().format("Invoke function success!%n");
				return returnMsg.getResult();
			}else {
				throw new RuntimeException("Invoke function failed!%n");
			}
		}
	}
}
