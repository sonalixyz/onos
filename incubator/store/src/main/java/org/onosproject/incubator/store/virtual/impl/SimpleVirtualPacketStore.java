/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.incubator.store.virtual.impl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.VirtualNetworkPacketStore;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketEvent;
import org.onosproject.net.packet.PacketRequest;
import org.onosproject.net.packet.PacketStoreDelegate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple single instance implementation of the virtual packet store.
 */
public class SimpleVirtualPacketStore
        extends AbstractVirtualStore<PacketEvent, PacketStoreDelegate>
        implements VirtualNetworkPacketStore {

    private Map<NetworkId, Map<TrafficSelector, Set<PacketRequest>>> requests
            = Maps.newConcurrentMap();

    @Override
    public void emit(NetworkId networkId, OutboundPacket packet) {
        notifyDelegate(networkId, new PacketEvent(PacketEvent.Type.EMIT, packet));
    }

    @Override
    public void requestPackets(NetworkId networkId, PacketRequest request) {
        requests.computeIfAbsent(networkId, k -> Maps.newConcurrentMap());

        requests.get(networkId).compute(request.selector(), (s, existingRequests) -> {
            if (existingRequests == null) {
                return ImmutableSet.of(request);
            } else if (!existingRequests.contains(request)) {
                if (hasDelegate(networkId)) {
                    delegateMap.get(networkId).requestPackets(request);
                }
                return ImmutableSet.<PacketRequest>builder()
                        .addAll(existingRequests)
                        .add(request)
                        .build();
            } else {
                return existingRequests;
            }
        });
    }

    @Override
    public void cancelPackets(NetworkId networkId, PacketRequest request) {
        requests.get(networkId).computeIfPresent(request.selector(), (s, existingRequests) -> {
            if (existingRequests.contains(request)) {
                HashSet<PacketRequest> newRequests = Sets.newHashSet(existingRequests);
                newRequests.remove(request);
                if (newRequests.size() > 0) {
                    return ImmutableSet.copyOf(newRequests);
                } else {
                    if (hasDelegate(networkId)) {
                        delegateMap.get(networkId).cancelPackets(request);
                    }
                    return null;
                }
            } else {
                return existingRequests;
            }
        });
    }

    @Override
    public List<PacketRequest> existingRequests(NetworkId networkId) {
        List<PacketRequest> list = Lists.newArrayList();
        requests.get(networkId).values().forEach(list::addAll);
        list.sort((o1, o2) -> o1.priority().priorityValue() - o2.priority().priorityValue());
        return list;
    }
}
