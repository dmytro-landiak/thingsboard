/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.server.service.install;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.service.install.update.DefaultDataUpdateService;

import java.util.List;

@Service
@Profile("install")
@Slf4j
@RequiredArgsConstructor
public class DefaultDatabaseSchemaVersionService implements DatabaseSchemaVersionService {

    private static final String CURRENT_PRODUCT = "CE";
    private static final List<String> SUPPORTED_VERSIONS_FOR_UPGRADE = List.of("3.8.0", "3.8.1");

    private final BuildProperties buildProperties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public String validateSchemaSettings(String upgradeFromVersion) {
        //TODO: remove after release (3.9.0)
       createProductIfNotExists();

        if (StringUtils.isNotEmpty(upgradeFromVersion) && DefaultDataUpdateService.getEnv("SKIP_SCHEMA_VERSION_CHECK", false)) {
            log.info("Skipped DB schema version check due to SKIP_SCHEMA_VERSION_CHECK set to 'true'.");
            return upgradeFromVersion;
        }

        String product = getProductFromDb();
        if (!CURRENT_PRODUCT.equals(product)) {
            onSchemaSettingsError("Upgrade failed: ThingsBoard " + product + " database using ThingsBoard " + CURRENT_PRODUCT);
        }

        Long schemaVersionFromDb = getSchemaVersionFromDb();
        if (schemaVersionFromDb == null) {
            onSchemaSettingsError("Upgrade failed: the database schema version is missing.");
        }

        long currentSchemaVersion = getCurrentSchemaVersion();

        if (currentSchemaVersion == schemaVersionFromDb) {
            onSchemaSettingsError("Upgrade failed: database already upgraded to current version. You can set SKIP_SCHEMA_VERSION_CHECK to 'true' if force re-upgrade needed.");
        }

        long major = schemaVersionFromDb / 1000000;
        long minor = (schemaVersionFromDb % 1000000) / 1000;
        long patch = schemaVersionFromDb % 1000;

        String currentSchemaVersionFromDb = major + "." + minor + "." + patch;

        if (!SUPPORTED_VERSIONS_FOR_UPGRADE.contains(currentSchemaVersionFromDb)) {
            onSchemaSettingsError(String.format("Upgrade failed: database version '%s' is not supported for upgrade. Supported versions are: %s.",
                    currentSchemaVersionFromDb, SUPPORTED_VERSIONS_FOR_UPGRADE
            ));
        }

        if (StringUtils.isEmpty(upgradeFromVersion)) {
            upgradeFromVersion = currentSchemaVersionFromDb;
        } else if (!SUPPORTED_VERSIONS_FOR_UPGRADE.contains(upgradeFromVersion)) {
            onSchemaSettingsError(String.format("Upgrade failed: 'versionFrom' '%s' is not supported for upgrade. Supported versions are: %s.",
                    upgradeFromVersion, SUPPORTED_VERSIONS_FOR_UPGRADE));
        }

        return upgradeFromVersion;
    }

    @Deprecated(forRemoval = true, since = "3.9.0")
    private void createProductIfNotExists() {
        boolean isCommunityEdition;
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM information_schema.tables WHERE table_name = 'integration'", Integer.class);
            isCommunityEdition = false;
        } catch (EmptyResultDataAccessException e) {
            isCommunityEdition = true;
        }
        String product = isCommunityEdition ? "CE" : "PE";
        jdbcTemplate.execute("ALTER TABLE tb_schema_settings ADD COLUMN IF NOT EXISTS product varchar(2) DEFAULT '" + product + "'");
    }

    @Override
    public void createSchemaSettings() {
        Long schemaVersion = getSchemaVersionFromDb();
        if (schemaVersion == null) {
            jdbcTemplate.execute("INSERT INTO tb_schema_settings (schema_version, product) VALUES (" + getCurrentSchemaVersion() + ", '" + CURRENT_PRODUCT + "')");
        }
    }

    @Override
    public void updateSchemaVersion() {
        jdbcTemplate.execute("UPDATE tb_schema_settings SET schema_version = " + getCurrentSchemaVersion());
    }

    private Long getSchemaVersionFromDb() {
        return jdbcTemplate.queryForList("SELECT schema_version FROM tb_schema_settings", Long.class).stream().findFirst().orElse(null);
    }

    private String getProductFromDb() {
        return jdbcTemplate.queryForList("SELECT product FROM tb_schema_settings", String.class).stream().findFirst().orElse(null);
    }

    private long getCurrentSchemaVersion() {
        String[] versionParts = buildProperties.getVersion().replaceAll("[^\\d.]", "").split("\\.");

        long major = Integer.parseInt(versionParts[0]);
        long minor = Integer.parseInt(versionParts[1]);
        long patch = versionParts.length > 2 ? Integer.parseInt(versionParts[2]) : 0;

        return major * 1000000 + minor * 1000 + patch;
    }

    private void onSchemaSettingsError(String message) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> log.info(message)));
        throw new RuntimeException(message);
    }
}
