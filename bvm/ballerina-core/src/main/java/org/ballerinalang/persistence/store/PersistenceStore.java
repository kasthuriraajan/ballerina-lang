/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.ballerinalang.persistence.store;

import org.ballerinalang.bre.bvm.WorkerExecutionContext;
import org.ballerinalang.persistence.Deserializer;
import org.ballerinalang.persistence.serializable.SerializableState;
import org.ballerinalang.persistence.states.State;
import org.ballerinalang.persistence.store.impl.FileBasedProvider;
import org.ballerinalang.runtime.Constants;
import org.ballerinalang.util.codegen.ProgramFile;
import org.ballerinalang.util.codegen.ResourceInfo;
import org.ballerinalang.util.program.BLangVMUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Representation of store which will be used to persist @{@link State}s in given storage.
 *
 * @since 0.976.0
 */
public class PersistenceStore {

    private static StorageProvider storageProvider = new FileBasedProvider();

    public static void persistState(State state) {
        SerializableState sState = new SerializableState(state.getContext());
        sState.setInstanceId(state.getInstanceId());
        String stateString = sState.serialize();
        String workerName = state.getContext().workerInfo.getWorkerName();
        storageProvider.persistState(state.getInstanceId(), workerName, stateString);
    }

    public static void removeStates(String instanceId) {
        storageProvider.removeStates(instanceId);
    }

    public static List<State> getStates(ProgramFile programFile) {
        List<State> states = new LinkedList<>();
        List<String> serializedStates = storageProvider.getAllSerializedStates();
        Deserializer deserializer = new Deserializer();
        for (String serializedState : serializedStates) {
            SerializableState sState = SerializableState.deserialize(serializedState);
            WorkerExecutionContext context = sState.getExecutionContext(programFile, deserializer);
            if (context.callableUnitInfo instanceof ResourceInfo) {
                ResourceInfo resourceInfo = (ResourceInfo) context.callableUnitInfo;
                BLangVMUtils.setServiceInfo(context, resourceInfo.getServiceInfo());
            }
            State state = new State(context, context.globalProps.get(Constants.STATE_ID).toString());
            // have to decrement ip as CPU class increments it as soon as instruction is fetched
            context.ip--;
            state.setIp(context.ip);
            states.add(state);
        }
        deserializer.cleanUpDeserializer();
        return states;
    }
}
