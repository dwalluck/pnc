/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.mock.datastore;

import org.jboss.pnc.api.enums.AlignmentPreference;
import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.model.Artifact;
import org.jboss.pnc.model.Base32LongID;
import org.jboss.pnc.model.BuildConfigSetRecord;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.BuildConfigurationAudited;
import org.jboss.pnc.model.BuildConfigurationSet;
import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.model.IdRev;
import org.jboss.pnc.model.User;
import org.jboss.pnc.spi.coordinator.BuildTask;
import org.jboss.pnc.spi.datastore.Datastore;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-11-24.
 */
@ApplicationScoped
public class DatastoreMock implements Datastore {

    private Logger log = LoggerFactory.getLogger(DatastoreMock.class.getName());

    private List<BuildRecord> buildRecords = Collections.synchronizedList(new ArrayList<>());

    private List<BuildConfigSetRecord> buildConfigSetRecords = Collections.synchronizedList(new ArrayList<>());

    private Map<Integer, BuildConfiguration> buildConfigurations = Collections.synchronizedMap(new HashMap<>());
    private Map<Integer, List<BuildConfigurationAudited>> buildConfigurationsAudited = Collections
            .synchronizedMap(new HashMap<>());

    AtomicLong buildRecordSetSequence = new AtomicLong(0);
    AtomicInteger buildConfigAuditedRevSequence = new AtomicInteger(0);

    private Set<IdRev> noRebuildRequiresIdRevs = new HashSet<>();

    @Override
    public Map<Artifact, String> checkForBuiltArtifacts(Collection<Artifact> artifacts) {
        return new HashMap<>();
    }

    @Override
    public BuildConfigurationAudited getBuildConfigurationAudited(IdRev idRev) {
        return buildConfigurationsAudited.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(idRev.id))
                .flatMap(entry -> entry.getValue().stream())
                .filter(bca -> bca.getIdRev().getRev().equals(idRev.rev))
                .findFirst()
                .orElse(null);
    }

    @Override
    public BuildRecord storeCompletedBuild(
            BuildRecord.Builder buildRecordBuilder,
            List<Artifact> builtArtifacts,
            List<Artifact> dependencies) {
        buildRecordBuilder.dependencies(dependencies);
        BuildRecord buildRecord = Mockito.spy(buildRecordBuilder.build());
        Mockito.when(buildRecord.getBuiltArtifacts()).thenReturn(new HashSet<>(builtArtifacts));
        BuildConfiguration buildConfiguration = buildRecord.getBuildConfigurationAudited().getBuildConfiguration();
        log.info("Storing build " + buildConfiguration);
        synchronized (this) {
            boolean exists = getBuildRecords().stream().anyMatch(br -> br.equals(buildRecord));
            if (exists) {
                throw new PersistenceException(
                        "Unique constraint violation, the record with id [" + buildRecord.getId()
                                + "] already exists.");
            }
            if (buildRecord.getBuildConfigSetRecord() != null) {
                buildRecord.getBuildConfigSetRecord().getBuildRecords().add(buildRecord);
            }
            buildRecords.add(buildRecord);
            log.debug("[{}]Build records after storing: {}", this.hashCode(), buildRecords);
        }
        return buildRecord;
    }

    @Override
    public BuildRecord storeRecordForNoRebuild(BuildRecord buildRecord) {
        buildRecords.add(buildRecord);
        return buildRecord;
    }

    @Override
    public User retrieveUserByUsername(String username) {
        User user = new User();
        user.setUsername("demo-user");
        return user;
    }

    public List<BuildRecord> getBuildRecords() {
        log.info("[{}]Getting build records {}", this.hashCode(), buildRecords); // mstodo remove
        return new ArrayList<>(buildRecords); // avoid concurrent modification exception
    }

    public List<BuildConfigSetRecord> getBuildConfigSetRecords() {
        return buildConfigSetRecords;
    }

    @Override
    public void createNewUser(User user) {
    }

    @Override
    public BuildConfigSetRecord saveBuildConfigSetRecord(BuildConfigSetRecord buildConfigSetRecord) {
        if (buildConfigSetRecord.getId() == null) {
            buildConfigSetRecord.setId(new Base32LongID(Sequence.nextId()));
        }
        log.info("Storing build config set record with id: " + buildConfigSetRecord);
        buildConfigSetRecords.add(buildConfigSetRecord);
        return buildConfigSetRecord;
    }

    @Override
    public BuildConfigurationAudited getLatestBuildConfigurationAudited(Integer buildConfigId) {
        BuildConfiguration buildConfig = buildConfigurations.get(buildConfigId);

        int rev = buildConfigAuditedRevSequence.incrementAndGet();
        BuildConfigurationAudited buildConfigurationAudited = BuildConfigurationAudited.Builder.newBuilder()
                .buildConfiguration(buildConfig)
                .rev(rev)
                .build();

        return buildConfigurationAudited;
    }

    @Override
    public BuildConfigurationAudited getLatestBuildConfigurationAuditedLoadBCDependencies(
            Integer buildConfigurationId) {
        List<BuildConfigurationAudited> auditedList = buildConfigurationsAudited.get(buildConfigurationId);
        return auditedList.get(auditedList.size() - 1);
    }

    @Override
    public BuildConfigSetRecord getBuildConfigSetRecordById(Base32LongID buildConfigSetRecordId) {
        return buildConfigSetRecords.stream()
                .filter(bcsr -> bcsr.getId().equals(buildConfigSetRecordId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean requiresRebuild(BuildTask task, Set<Integer> processedDependenciesCache) {
        return true;
    }

    @Override
    public Set<BuildConfiguration> getBuildConfigurations(BuildConfigurationSet buildConfigurationSet) {
        return buildConfigurationSet.getBuildConfigurations();
    }

    @Override
    public Collection<BuildConfigSetRecord> findBuildConfigSetRecordsInProgress() {
        return buildConfigSetRecords.stream().filter(r -> !r.getStatus().isFinal()).collect(Collectors.toList());
    }

    @Override
    public boolean requiresRebuild(
            BuildConfigurationAudited buildConfigurationAudited,
            boolean checkImplicitDependencies,
            boolean temporaryBuild,
            AlignmentPreference alignmentPreference,
            Set<Integer> processedDependenciesCache,
            Consumer<BuildRecord> nonRebuildCauseSetter) {
        if (noRebuildRequiresIdRevs.contains(buildConfigurationAudited.getIdRev())) {
            nonRebuildCauseSetter
                    .accept(BuildRecord.Builder.newBuilder().id(new Base32LongID(Sequence.nextId())).build());
            return false;
        } else {
            return true;
        }
    }

    public BuildConfiguration save(BuildConfiguration buildConfig) {
        return saveBCA(buildConfig).getBuildConfiguration();
    }

    public BuildConfigurationAudited saveBCA(BuildConfiguration buildConfig) {
        List<BuildConfigurationAudited> auditedConfigs = buildConfigurationsAudited
                .computeIfAbsent(buildConfig.getId(), (k) -> new ArrayList<>());

        var bca = BuildConfigurationAudited
                .fromBuildConfiguration(buildConfig, buildConfigAuditedRevSequence.getAndIncrement());
        auditedConfigs.add(bca);
        buildConfigurations.put(buildConfig.getId(), bca.getBuildConfiguration());

        return bca;
    }

    public void clear() {
        log.info("Clearing build records");
        buildRecords.clear();
        buildConfigSetRecords.clear();
        buildConfigurations.clear();
        buildRecordSetSequence = new AtomicLong(0);
        buildConfigAuditedRevSequence = new AtomicInteger(0);
    }

    public boolean addNoRebuildRequiredBCAIdREv(IdRev idRev) {
        return noRebuildRequiresIdRevs.add(idRev);
    }

    public boolean removeNoRebuildRequiredBCAIdREv(IdRev idRev) {
        return noRebuildRequiresIdRevs.remove(idRev);
    }
}
