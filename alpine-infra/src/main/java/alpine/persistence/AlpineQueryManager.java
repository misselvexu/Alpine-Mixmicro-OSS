/*
 * This file is part of Alpine.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package alpine.persistence;

import alpine.security.ApiKeyGenerator;
import alpine.event.LdapSyncEvent;
import alpine.event.framework.EventService;
import alpine.event.framework.LoggableSubscriber;
import alpine.event.framework.Subscriber;
import alpine.common.logging.Logger;
import alpine.model.ApiKey;
import alpine.model.ConfigProperty;
import alpine.model.EventServiceLog;
import alpine.model.LdapUser;
import alpine.model.ManagedUser;
import alpine.model.MappedLdapGroup;
import alpine.model.MappedOidcGroup;
import alpine.model.OidcGroup;
import alpine.model.OidcUser;
import alpine.model.Permission;
import alpine.model.Team;
import alpine.model.UserPrincipal;
import alpine.resources.AlpineRequest;
import io.jsonwebtoken.lang.Collections;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This QueryManager provides a concrete extension of {@link AbstractAlpineQueryManager} by
 * providing methods that operate on the default Alpine models such as ManagedUser and Team.
 *
 * @author Steve Springett
 * @since 1.0.0
 */
public class AlpineQueryManager extends AbstractAlpineQueryManager {

    private static final Logger LOGGER = Logger.getLogger(AlpineQueryManager.class);

    /**
     * Default constructor.
     */
    public AlpineQueryManager() {
        super();
    }

    /**
     * Constructs a new AlpineQueryManager.
     * @param pm a PersistenceManager
     */
    public AlpineQueryManager(final PersistenceManager pm) {
        super(pm);
    }

    /**
     * Constructs a new AlpineQueryManager.
     * @param request an AlpineRequest
     */
    public AlpineQueryManager(final AlpineRequest request) {
        super(request);
    }

    /**
     * Constructs a new AlpineQueryManager.
     * @param pm a PersistenceManager
     * @param request an AlpineRequest
     * @since 1.9.3
     */
    public AlpineQueryManager(final PersistenceManager pm, final AlpineRequest request) {
        super(pm, request);
    }

    /**
     * Returns an API key.
     * @param key the key to return
     * @return an ApiKey
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public ApiKey getApiKey(final String key) {
        final Query query = pm.newQuery(ApiKey.class, "key == :key");
        final List<ApiKey> result = (List<ApiKey>) query.execute(key);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Regenerates an API key. This method does not create a new ApiKey object,
     * rather it uses the existing ApiKey object and simply creates a new
     * key string.
     * @param apiKey the ApiKey object to regenerate the key of.
     * @return an ApiKey
     * @since 1.0.0
     */
    public ApiKey regenerateApiKey(final ApiKey apiKey) {
        pm.currentTransaction().begin();
        apiKey.setKey(ApiKeyGenerator.generate());
        pm.currentTransaction().commit();
        return pm.getObjectById(ApiKey.class, apiKey.getId());
    }

    /**
     * Creates a new ApiKey object, including a cryptographically secure
     * API key string.
     * @param team The team to create the key for
     * @return an ApiKey
     */
    public ApiKey createApiKey(final Team team) {
        final List<Team> teams = new ArrayList<>();
        teams.add(team);
        pm.currentTransaction().begin();
        final ApiKey apiKey = new ApiKey();
        apiKey.setKey(ApiKeyGenerator.generate());
        apiKey.setTeams(teams);
        pm.makePersistent(apiKey);
        pm.currentTransaction().commit();
        return pm.getObjectById(ApiKey.class, apiKey.getId());
    }

    /**
     * Creates a new OidcUser object with the specified username.
     * @param username The username of the new OidcUser. This must reference an
     *                 existing username in the OpenID Connect identity provider.
     * @return an LdapUser
     * @since 1.8.0
     */
    public OidcUser createOidcUser(final String username) {
        pm.currentTransaction().begin();
        final OidcUser user = new OidcUser();
        user.setUsername(username);
        // Subject identifier and email will be synced when a
        // user with the given username signs in for the first time
        pm.makePersistent(user);
        pm.currentTransaction().commit();
        return getObjectById(OidcUser.class, user.getId());
    }

    /**
     * Updates the specified OidcUser.
     * @param transientUser the optionally detached OidcUser object to update.
     * @return an OidcUser
     * @since 1.8.0
     */
    public OidcUser updateOidcUser(final OidcUser transientUser) {
        final OidcUser user = getObjectById(OidcUser.class, transientUser.getId());
        pm.currentTransaction().begin();
        user.setSubjectIdentifier(transientUser.getSubjectIdentifier());
        pm.currentTransaction().commit();
        return pm.getObjectById(OidcUser.class, user.getId());
    }

    /**
     * Retrieves an OidcUser containing the specified username. If the username
     * does not exist, returns null.
     * @param username The username to retrieve
     * @return an OidcUser
     * @since 1.8.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public OidcUser getOidcUser(final String username) {
        final Query query = pm.newQuery(OidcUser.class, "username == :username");
        final List<OidcUser> result = (List<OidcUser>) query.execute(username);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Returns a complete list of all OidcUser objects, in ascending order by username.
     * @return a list of OidcUser
     * @since 1.8.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<OidcUser> getOidcUsers() {
        final Query query = pm.newQuery(OidcUser.class);
        query.setOrdering("username asc");
        return (List<OidcUser>) query.execute();
    }

    /**
     * Creates a OidcGroup.
     * @param name Name of the group to create
     * @return a OidcGroup
     * @since 1.8.0
     */
    public OidcGroup createOidcGroup(final String name) {
        pm.currentTransaction().begin();
        final OidcGroup group = new OidcGroup();
        group.setName(name);
        pm.makePersistent(group);
        pm.currentTransaction().commit();
        return getObjectByUuid(OidcGroup.class, group.getUuid());
    }

    /**
     * Updates a OidcGroup.
     * @param oidcGroup The group to update
     * @return a refreshed OidcGroup
     * @since 1.8.0
     */
    public OidcGroup updateOidcGroup(final OidcGroup oidcGroup) {
        final OidcGroup oidcGroupToUpdate = getObjectByUuid(OidcGroup.class, oidcGroup.getUuid());
        pm.currentTransaction().begin();
        oidcGroupToUpdate.setName(oidcGroup.getName());
        pm.currentTransaction().commit();
        return pm.getObjectById(OidcGroup.class, oidcGroupToUpdate.getId());
    }

    /**
     * Returns a complete list of all OidcGroup objects, in ascending order by name.
     * @return a list of OidcGroup
     * @since 1.8.0
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<OidcGroup> getOidcGroups() {
        final Query query = pm.newQuery(OidcGroup.class);
        query.setOrdering("name asc");
        return (List<OidcGroup>) query.execute();
    }

    /**
     * Returns an OidcGroup containing the specified name. If the name
     * does not exist, returns null.
     * @param name Name of the group to retrieve
     * @return an OidcGroup
     * @since 1.8.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public OidcGroup getOidcGroup(final String name) {
        final Query query = pm.newQuery(OidcGroup.class, "name == :name");
        final List<OidcGroup> result = (List<OidcGroup>) query.execute(name);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * This method dynamically assigns team membership to the specified user from
     * the list of OpenID Connect groups the user is a member of. The method will look
     * up any {@link MappedOidcGroup}s and ensure the user is only a member of the
     * teams that have a mapping to an OpenID Connect group for which the user is a member.
     * @param user the OpenID Connect user to sync team membership for
     * @param groupNames a list of OpenID Connect groups the user is a member of
     * @return a refreshed OidcUser object
     * @since 1.8.0
     */
    public OidcUser synchronizeTeamMembership(final OidcUser user, final List<String> groupNames) {
        LOGGER.debug("Synchronizing team membership for OpenID Connect user " + user.getUsername());
        final List<Team> removeThese = new ArrayList<>();

        if (user.getTeams() != null) {
            for (final Team team : user.getTeams()) {
                LOGGER.debug(user.getUsername() + " is a member of team: " + team.getName());
                if (team.getMappedOidcGroups() != null && !team.getMappedOidcGroups().isEmpty()) {
                    for (final MappedOidcGroup mappedOidcGroup : team.getMappedOidcGroups()) {
                        LOGGER.debug(mappedOidcGroup.getGroup().getName() + " is mapped to team: " + team.getName());
                        if (!groupNames.contains(mappedOidcGroup.getGroup().getName())) {
                            LOGGER.debug(mappedOidcGroup.getGroup().getName() + " is not identified in the List of groups specified. Queuing removal of membership for user " + user.getUsername());
                            removeThese.add(team);
                        }
                    }
                } else {
                    LOGGER.debug(team.getName() + " does not have any mapped OpenID Connect groups. Queuing removal of " + user.getUsername() + " from team: " + team.getName());
                    removeThese.add(team);
                }
            }
        }

        for (final Team team : removeThese) {
            LOGGER.debug("Removing user: " + user.getUsername() + " from team: " + team.getName());
            removeUserFromTeam(user, team);
        }

        for (final String groupName : groupNames) {
            final OidcGroup group = getOidcGroup(groupName);
            if (group == null) {
                LOGGER.debug("Unknown OpenID Connect group " + groupName);
                continue;
            }

            for (final MappedOidcGroup mappedOidcGroup : getMappedOidcGroups(group)) {
                LOGGER.debug("Adding user: " + user.getUsername() + " to team: " + mappedOidcGroup.getTeam().getName());
                addUserToTeam(user, mappedOidcGroup.getTeam());
            }
        }

        return getObjectById(OidcUser.class, user.getId());
    }

    /**
     * Retrieves an LdapUser containing the specified username. If the username
     * does not exist, returns null.
     * @param username The username to retrieve
     * @return an LdapUser
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public LdapUser getLdapUser(final String username) {
        final Query query = pm.newQuery(LdapUser.class, "username == :username");
        final List<LdapUser> result = (List<LdapUser>) query.execute(username);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Returns a complete list of all LdapUser objects, in ascending order by username.
     * @return a list of LdapUsers
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public List<LdapUser> getLdapUsers() {
        final Query query = pm.newQuery(LdapUser.class);
        query.setOrdering("username asc");
        return (List<LdapUser>) query.execute();
    }

    /**
     * Creates a new LdapUser object with the specified username.
     * @param username The username of the new LdapUser. This must reference an existing username in the directory service
     * @return an LdapUser
     * @since 1.0.0
     */
    public LdapUser createLdapUser(final String username) {
        pm.currentTransaction().begin();
        final LdapUser user = new LdapUser();
        user.setUsername(username);
        user.setDN("Syncing...");
        pm.makePersistent(user);
        pm.currentTransaction().commit();
        EventService.getInstance().publish(new LdapSyncEvent(user.getUsername()));
        return getObjectById(LdapUser.class, user.getId());
    }

    /**
     * Updates the specified LdapUser.
     * @param transientUser the optionally detached LdapUser object to update.
     * @return an LdapUser
     * @since 1.0.0
     */
    public LdapUser updateLdapUser(final LdapUser transientUser) {
        final LdapUser user = getObjectById(LdapUser.class, transientUser.getId());
        pm.currentTransaction().begin();
        user.setDN(transientUser.getDN());
        pm.currentTransaction().commit();
        return pm.getObjectById(LdapUser.class, user.getId());
    }

    /**
     * This method dynamically assigns team membership to the specified user from
     * the list of LDAP group DN's the user is a member of. The method will look
     * up any {@link MappedLdapGroup}s and ensure the user is only a member of the
     * teams that have a mapping to an LDAP group for which the user is a member.
     * @param user the LDAP user to sync team membership for
     * @param groupDNs a list of LDAP group DNs the user is a member of
     * @return a refreshed LdapUser object
     * @since 1.4.0
     */
    public LdapUser synchronizeTeamMembership(final LdapUser user, final List<String> groupDNs) {
        LOGGER.debug("Synchronizing team membership for " + user.getUsername());
        final List<Team> removeThese = new ArrayList<>();
        if (user.getTeams() != null) {
            for (final Team team : user.getTeams()) {
                LOGGER.debug(user.getUsername() + " is a member of team: " + team.getName());
                if (team.getMappedLdapGroups() != null) {
                    for (final MappedLdapGroup mappedLdapGroup : team.getMappedLdapGroups()) {
                        LOGGER.debug(mappedLdapGroup.getDn() + " is mapped to team: " + team.getName());
                        if (!groupDNs.contains(mappedLdapGroup.getDn())) {
                            LOGGER.debug(mappedLdapGroup.getDn() + " is not identified in the List of group DNs specified. Queuing removal of membership for user " + user.getUsername());
                            removeThese.add(team);
                        }
                    }
                } else {
                    LOGGER.debug(team.getName() + " does not have any mapped LDAP groups. Queuing removal of " + user.getUsername() + " from team: " + team.getName());
                    removeThese.add(team);
                }
            }
        }
        for (final Team team: removeThese) {
            LOGGER.debug("Removing user: " + user.getUsername() + " from team: " + team.getName());
            removeUserFromTeam(user, team);
        }
        for (final String groupDN: groupDNs) {
            for (final MappedLdapGroup mappedLdapGroup: getMappedLdapGroups(groupDN)) {
                LOGGER.debug("Adding user: " + user.getUsername() + " to team: " + mappedLdapGroup.getTeam());
                addUserToTeam(user, mappedLdapGroup.getTeam());
            }
        }
        return getObjectById(LdapUser.class, user.getId());
    }

    /**
     * Creates a new ManagedUser object.
     * @param username The username for the user
     * @param passwordHash The hashed password.
     * @return a ManagedUser
     * @since 1.0.0
     */
    public ManagedUser createManagedUser(final String username, final String passwordHash) {
        return createManagedUser(username, null, null, passwordHash, false, false, false);
    }

    /**
     * Creates a new ManagedUser object.
     * @param username The username for the user
     * @param fullname The fullname of the user
     * @param email The users email address
     * @param passwordHash The hashed password
     * @param forcePasswordChange Whether or not user needs to change password on next login or not
     * @param nonExpiryPassword Whether or not the users password ever expires or not
     * @param suspended Whether or not user being created is suspended or not
     * @return a ManagedUser
     * @since 1.1.0
     */
    public ManagedUser createManagedUser(final String username, final String fullname, final String email,
                                         final String passwordHash, final boolean forcePasswordChange,
                                         final boolean nonExpiryPassword, final boolean suspended) {
        pm.currentTransaction().begin();
        final ManagedUser user = new ManagedUser();
        user.setUsername(username);
        user.setFullname(fullname);
        user.setEmail(email);
        user.setPassword(passwordHash);
        user.setForcePasswordChange(forcePasswordChange);
        user.setNonExpiryPassword(nonExpiryPassword);
        user.setSuspended(suspended);
        user.setLastPasswordChange(new Date());
        pm.makePersistent(user);
        pm.currentTransaction().commit();
        return getObjectById(ManagedUser.class, user.getId());
    }

    /**
     * Updates the specified ManagedUser.
     * @param transientUser the optionally detached ManagedUser object to update.
     * @return an ManagedUser
     * @since 1.0.0
     */
    public ManagedUser updateManagedUser(final ManagedUser transientUser) {
        final ManagedUser user = getObjectById(ManagedUser.class, transientUser.getId());
        pm.currentTransaction().begin();
        user.setFullname(transientUser.getFullname());
        user.setEmail(transientUser.getEmail());
        user.setForcePasswordChange(transientUser.isForcePasswordChange());
        user.setNonExpiryPassword(transientUser.isNonExpiryPassword());
        user.setSuspended(transientUser.isSuspended());
        if (transientUser.getPassword() != null) {
            if (!user.getPassword().equals(transientUser.getPassword())) {
                user.setLastPasswordChange(new Date());
            }
            user.setPassword(transientUser.getPassword());
        }
        pm.currentTransaction().commit();
        return pm.getObjectById(ManagedUser.class, user.getId());
    }

    /**
     * Returns a ManagedUser with the specified username. If the username
     * does not exist, returns null.
     * @param username The username to retrieve
     * @return a ManagedUser
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public ManagedUser getManagedUser(final String username) {
        final Query query = pm.newQuery(ManagedUser.class, "username == :username");
        final List<ManagedUser> result = (List<ManagedUser>) query.execute(username);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Returns a complete list of all ManagedUser objects, in ascending order by username.
     * @return a List of ManagedUsers
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public List<ManagedUser> getManagedUsers() {
        final Query query = pm.newQuery(ManagedUser.class);
        query.setOrdering("username asc");
        return (List<ManagedUser>) query.execute();
    }

    /**
     * Resolves a UserPrincipal. Default order resolution is to first match
     * on ManagedUser then on LdapUser and finally on OidcUser. This may be
     * configurable in a future release.
     * @param username the username of the principal to retrieve
     * @return a UserPrincipal if found, null if not found
     * @since 1.0.0
     */
    public UserPrincipal getUserPrincipal(String username) {
        UserPrincipal principal = getManagedUser(username);
        if (principal != null) {
            return principal;
        }
        principal = getLdapUser(username);
        if (principal != null) {
            return principal;
        }
        return getOidcUser(username);
    }

    /**
     * Creates a new Team with the specified name. If createApiKey is true,
     * then {@link #createApiKey} is invoked and a cryptographically secure
     * API key is generated.
     * @param name The name of th team
     * @param createApiKey whether or not to create an API key for the team
     * @return a Team
     * @since 1.0.0
     */
    public Team createTeam(final String name, final boolean createApiKey) {
        pm.currentTransaction().begin();
        final Team team = new Team();
        team.setName(name);
        //todo assign permissions
        pm.makePersistent(team);
        pm.currentTransaction().commit();
        if (createApiKey) {
            createApiKey(team);
        }
        return getObjectByUuid(Team.class, team.getUuid(), Team.FetchGroup.ALL.name());
    }

    /**
     * Returns a complete list of all Team objects, in ascending order by name.
     * @return a List of Teams
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public List<Team> getTeams() {
        pm.getFetchPlan().addGroup(Team.FetchGroup.ALL.name());
        final Query query = pm.newQuery(Team.class);
        query.setOrdering("name asc");
        return (List<Team>) query.execute();
    }

    /**
     * Updates the specified Team.
     * @param transientTeam the optionally detached Team object to update
     * @return a Team
     * @since 1.0.0
     */
    public Team updateTeam(final Team transientTeam) {
        final Team team = getObjectByUuid(Team.class, transientTeam.getUuid());
        pm.currentTransaction().begin();
        team.setName(transientTeam.getName());
        //todo assign permissions
        pm.currentTransaction().commit();
        return pm.getObjectById(Team.class, team.getId());
    }

    /**
     * Associates a UserPrincipal to a Team.
     * @param user The user to bind
     * @param team The team to bind
     * @return true if operation was successful, false if not. This is not an indication of team association,
     * an unsuccessful return value may be due to the team or user not existing, or a binding that already
     * exists between the two.
     * @since 1.0.0
     */
    public boolean addUserToTeam(final UserPrincipal user, final Team team) {
        List<Team> teams = user.getTeams();
        boolean found = false;
        if (teams == null) {
            teams = new ArrayList<>();
        }
        for (final Team t: teams) {
            if (team.getUuid().equals(t.getUuid())) {
                found = true;
            }
        }
        if (!found) {
            pm.currentTransaction().begin();
            teams.add(team);
            user.setTeams(teams);
            pm.currentTransaction().commit();
            return true;
        }
        return false;
    }

    /**
     * Removes the association of a UserPrincipal to a Team.
     * @param user The user to unbind
     * @param team The team to unbind
     * @return true if operation was successful, false if not. This is not an indication of team disassociation,
     * an unsuccessful return value may be due to the team or user not existing, or a binding that may not exist.
     * @since 1.0.0
     */
    public boolean removeUserFromTeam(final UserPrincipal user, final Team team) {
        final List<Team> teams = user.getTeams();
        if (teams == null) {
            return false;
        }
        boolean found = false;
        for (final Team t: teams) {
            if (team.getUuid().equals(t.getUuid())) {
                found = true;
            }
        }
        if (found) {
            pm.currentTransaction().begin();
            teams.remove(team);
            user.setTeams(teams);
            pm.currentTransaction().commit();
            return true;
        }
        return false;
    }

    /**
     * Creates a Permission object.
     * @param name The name of the permission
     * @param description the permissions description
     * @return a Permission
     * @since 1.1.0
     */
    public Permission createPermission(final String name, final String description) {
        pm.currentTransaction().begin();
        final Permission permission = new Permission();
        permission.setName(name);
        permission.setDescription(description);
        pm.makePersistent(permission);
        pm.currentTransaction().commit();
        return getObjectById(Permission.class, permission.getId());
    }

    /**
     * Retrieves a Permission by its name.
     * @param name The name of the permission
     * @return a Permission
     * @since 1.1.0
     */
    @SuppressWarnings("unchecked")
    public Permission getPermission(final String name) {
        final Query query = pm.newQuery(Permission.class, "name == :name");
        final List<Permission> result = (List<Permission>) query.execute(name);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Returns a list of all Permissions defined in the system.
     * @return a List of Permission objects
     * @since 1.1.0
     */
    @SuppressWarnings("unchecked")
    public List<Permission> getPermissions() {
        final Query query = pm.newQuery(Permission.class);
        query.setOrdering("name asc");
        return (List<Permission>) query.execute();
    }

    /**
     * Determines the effective permissions for the specified user by collecting
     * a List of all permissions assigned to the user either directly, or through
     * team membership.
     * @param user the user to retrieve permissions for
     * @return a List of Permission objects
     * @since 1.1.0
     */
    public List<Permission> getEffectivePermissions(UserPrincipal user) {
        final LinkedHashSet<Permission> permissions = new LinkedHashSet<>();
        if (user.getPermissions() != null) {
            permissions.addAll(user.getPermissions());
        }
        if (user.getTeams() != null) {
            for (final Team team: user.getTeams()) {
                final List<Permission> teamPermissions = getObjectById(Team.class, team.getId()).getPermissions();
                if (teamPermissions != null) {
                    permissions.addAll(teamPermissions);
                }
            }
        }
        return new ArrayList<>(permissions);
    }

    /**
     * Determines if the specified UserPrincipal has been assigned the specified permission.
     * @param user the UserPrincipal to query
     * @param permissionName the name of the permission
     * @return true if the user has the permission assigned, false if not
     * @since 1.0.0
     */
    public boolean hasPermission(final UserPrincipal user, String permissionName) {
        return hasPermission(user, permissionName, false);
    }

    /**
     * Determines if the specified UserPrincipal has been assigned the specified permission.
     * @param user the UserPrincipal to query
     * @param permissionName the name of the permission
     * @param includeTeams if true, will query all Team membership assigned to the user for the specified permission
     * @return true if the user has the permission assigned, false if not
     * @since 1.0.0
     */
    public boolean hasPermission(final UserPrincipal user, String permissionName, boolean includeTeams) {
        Query query;
        if (user instanceof ManagedUser) {
            query = pm.newQuery(Permission.class, "name == :permissionName && managedUsers.contains(:user)");
        } else if (user instanceof LdapUser) {
            query = pm.newQuery(Permission.class, "name == :permissionName && ldapUsers.contains(:user)");
        } else {
            query = pm.newQuery(Permission.class, "name == :permissionName && oidcUsers.contains(:user)");
        }
        query.setResult("count(id)");
        final long count = (Long) query.execute(permissionName, user);
        if (count > 0) {
            return true;
        }
        if (includeTeams) {
            for (final Team team: user.getTeams()) {
                if (hasPermission(team, permissionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines if the specified Team has been assigned the specified permission.
     * @param team the Team to query
     * @param permissionName the name of the permission
     * @return true if the team has the permission assigned, false if not
     * @since 1.0.0
     */
    public boolean hasPermission(final Team team, String permissionName) {
        final Query query = pm.newQuery(Permission.class, "name == :permissionName && teams.contains(:team)");
        query.setResult("count(id)");
        return (Long) query.execute(permissionName, team) > 0;
    }

    /**
     * Determines if the specified ApiKey has been assigned the specified permission.
     * @param apiKey the ApiKey to query
     * @param permissionName the name of the permission
     * @return true if the apiKey has the permission assigned, false if not
     * @since 1.1.1
     */
    public boolean hasPermission(final ApiKey apiKey, String permissionName) {
        if (apiKey.getTeams() == null) {
            return false;
        }
        for (final Team team: apiKey.getTeams()) {
            final List<Permission> teamPermissions = getObjectById(Team.class, team.getId()).getPermissions();
            for (final Permission permission: teamPermissions) {
                if (permission.getName().equals(permissionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Retrieves a MappedLdapGroup object for the specified Team and LDAP group.
     * @param team a Team object
     * @param dn a String representation of Distinguished Name
     * @return a MappedLdapGroup if found, or null if no mapping exists
     * @since 1.4.0
     */
    @SuppressWarnings("unchecked")
    public MappedLdapGroup getMappedLdapGroup(final Team team, final String dn) {
        final Query query = pm.newQuery(MappedLdapGroup.class, "team == :team && dn == :dn");
        final List<MappedLdapGroup> result = (List<MappedLdapGroup>) query.execute(team, dn);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Retrieves a List of MappedLdapGroup objects for the specified Team.
     * @param team a Team object
     * @return a List of MappedLdapGroup objects
     * @since 1.4.0
     */
    @SuppressWarnings("unchecked")
    public List<MappedLdapGroup> getMappedLdapGroups(final Team team) {
        final Query query = pm.newQuery(MappedLdapGroup.class, "team == :team");
        return (List<MappedLdapGroup>) query.execute(team);
    }

    /**
     * Retrieves a List of MappedLdapGroup objects for the specified DN.
     * @param dn a String representation of Distinguished Name
     * @return a List of MappedLdapGroup objects
     * @since 1.4.0
     */
    @SuppressWarnings("unchecked")
    public List<MappedLdapGroup> getMappedLdapGroups(final String dn) {
        final Query query = pm.newQuery(MappedLdapGroup.class, "dn == :dn");
        return (List<MappedLdapGroup>) query.execute(dn);
    }

    /**
     * Determines if the specified Team is mapped to the specified LDAP group.
     * @param team a Team object
     * @param dn a String representation of Distinguished Name
     * @return true if a mapping exists, false if not
     * @since 1.4.0
     */
    public boolean isMapped(final Team team, final String dn) {
        return getMappedLdapGroup(team, dn) != null;
    }

    /**
     * Creates a MappedLdapGroup object.
     * @param team The team to map
     * @param dn the distinguished name of the LDAP group to map
     * @return a MappedLdapGroup
     * @since 1.4.0
     */
    public MappedLdapGroup createMappedLdapGroup(final Team team, final String dn) {
        pm.currentTransaction().begin();
        final MappedLdapGroup mapping = new MappedLdapGroup();
        mapping.setTeam(team);
        mapping.setDn(dn);
        pm.makePersistent(mapping);
        pm.currentTransaction().commit();
        return getObjectById(MappedLdapGroup.class, mapping.getId());
    }

    /**
     * Creates a MappedOidcGroup object.
     * @param team The team to map
     * @param group The OIDC group to map
     * @return a MappedOidcGroup
     * @since 1.8.0
     */
    public MappedOidcGroup createMappedOidcGroup(final Team team, final OidcGroup group) {
        pm.currentTransaction().begin();
        final MappedOidcGroup mapping = new MappedOidcGroup();
        mapping.setTeam(team);
        mapping.setGroup(group);
        pm.makePersistent(mapping);
        pm.currentTransaction().commit();
        return getObjectById(MappedOidcGroup.class, mapping.getId());
    }

    /**
     * Retrieves a MappedOidcGroup object for the specified Team and OIDC group.
     * @param team a Team object
     * @param group a OidcGroup object
     * @return a MappedOidcGroup if found, or null if no mapping exists
     * @since 1.8.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MappedOidcGroup getMappedOidcGroup(final Team team, final OidcGroup group) {
        final Query query = pm.newQuery(MappedOidcGroup.class, "team == :team && group == :group");
        final List<MappedOidcGroup> result = (List<MappedOidcGroup>) query.execute(team, group);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Retrieves a List of MappedOidcGroup objects for the specified Team.
     * @param team The team to retrieve mappings for
     * @return a List of MappedOidcGroup objects
     * @since 1.8.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<MappedOidcGroup> getMappedOidcGroups(final Team team) {
        final Query query = pm.newQuery(MappedOidcGroup.class, "team == :team");
        return (List<MappedOidcGroup>) query.execute(team);
    }

    /**
     * Retrieves a List of MappedOidcGroup objects for the specified group.
     * @param group The group to retrieve mappings for
     * @return a List of MappedOidcGroup objects
     * @since 1.8.0
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<MappedOidcGroup> getMappedOidcGroups(final OidcGroup group) {
        final Query query = pm.newQuery(MappedOidcGroup.class, "group == :group");
        return (List<MappedOidcGroup>) query.execute(group);
    }

    /**
     * Determines if the specified Team is mapped to the specified OpenID Connect group.
     * @param team a Team object
     * @param group a OidcGroup object
     * @return true if a mapping exists, false if not
     * @since 1.8.0
     */
    public boolean isOidcGroupMapped(final Team team, final OidcGroup group) {
        return getMappedOidcGroup(team, group) != null;
    }

    /**
     * Creates a new EventServiceLog. This method will automatically determine
     * if the subscriber is an implementation of {@link LoggableSubscriber} and
     * if so, will log the event. If not, then nothing will be logged and this
     * method will return null.
     * @param clazz the class of the subscriber task that handles the event
     * @return a new EventServiceLog
     */
    public EventServiceLog createEventServiceLog(Class<? extends Subscriber> clazz) {
        if (LoggableSubscriber.class.isAssignableFrom(clazz)) {
            pm.currentTransaction().begin();
            final EventServiceLog log = new EventServiceLog();
            log.setSubscriberClass(clazz.getCanonicalName());
            log.setStarted(new Timestamp(new Date().getTime()));
            pm.makePersistent(log);
            pm.currentTransaction().commit();
            return getObjectById(EventServiceLog.class, log.getId());
        }
        return null;
    }

    /**
     * Updates a EventServiceLog.
     * @param eventServiceLog the EventServiceLog to update
     * @return an updated EventServiceLog
     */
    public EventServiceLog updateEventServiceLog(EventServiceLog eventServiceLog) {
        if (eventServiceLog != null) {
            final EventServiceLog log = getObjectById(EventServiceLog.class, eventServiceLog.getId());
            if (log != null) {
                pm.currentTransaction().begin();
                log.setCompleted(new Timestamp(new Date().getTime()));
                pm.currentTransaction().commit();
                return pm.getObjectById(EventServiceLog.class, log.getId());
            }
        }
        return null;
    }

    /**
     * Returns the most recent log entry for the specified Subscriber.
     * If no log entries are found, this method will return null.
     * @param clazz The LoggableSubscriber class to query on
     * @return a EventServiceLog
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public EventServiceLog getLatestEventServiceLog(final Class<LoggableSubscriber> clazz) {
        final Query query = pm.newQuery(EventServiceLog.class, "eventClass == :clazz");
        query.setOrdering("completed desc");
        final List<EventServiceLog> result = (List<EventServiceLog>) query.execute(clazz);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Returns a ConfigProperty with the specified groupName and propertyName.
     * @param groupName the group name of the config property
     * @param propertyName the name of the property
     * @return a ConfigProperty object
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    public ConfigProperty getConfigProperty(final String groupName, final String propertyName) {
        final Query query = pm.newQuery(ConfigProperty.class, "groupName == :groupName && propertyName == :propertyName");
        final List<ConfigProperty> result = (List<ConfigProperty>) query.execute(groupName, propertyName);
        return Collections.isEmpty(result) ? null : result.get(0);
    }

    /**
     * Returns a list of ConfigProperty objects with the specified groupName.
     * @param groupName the group name of the properties
     * @return a List of ConfigProperty objects
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    public List<ConfigProperty> getConfigProperties(final String groupName) {
        final Query query = pm.newQuery(ConfigProperty.class, "groupName == :groupName");
        query.setOrdering("propertyName asc");
        return (List<ConfigProperty>) query.execute(groupName);
    }

    /**
     * Returns a list of ConfigProperty objects.
     * @return a List of ConfigProperty objects
     * @since 1.3.0
     */
    @SuppressWarnings("unchecked")
    public List<ConfigProperty> getConfigProperties() {
        final Query query = pm.newQuery(ConfigProperty.class);
        query.setOrdering("groupName asc, propertyName asc");
        return (List<ConfigProperty>) query.execute();
    }

    /**
     * Creates a ConfigProperty object.
     * @param groupName the group name of the property
     * @param propertyName the name of the property
     * @param propertyValue the value of the property
     * @param propertyType the type of property
     * @param description a description of the property
     * @return a ConfigProperty object
     * @since 1.3.0
     */
    public ConfigProperty createConfigProperty(final String groupName, final String propertyName,
                                               final String propertyValue, final ConfigProperty.PropertyType propertyType,
                                               final String description) {
        pm.currentTransaction().begin();
        final ConfigProperty configProperty = new ConfigProperty();
        configProperty.setGroupName(groupName);
        configProperty.setPropertyName(propertyName);
        configProperty.setPropertyValue(propertyValue);
        configProperty.setPropertyType(propertyType);
        configProperty.setDescription(description);
        pm.makePersistent(configProperty);
        pm.currentTransaction().commit();
        return getObjectById(ConfigProperty.class, configProperty.getId());
    }

}
