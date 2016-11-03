/*
 * Copyright 2016 Code Above Lab LLC
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

package com.codeabovelab.dm.cluman.ui;

import com.codeabovelab.dm.cluman.model.NotFoundException;
import com.codeabovelab.dm.cluman.security.AbstractAclService;
import com.codeabovelab.dm.cluman.security.AuthoritiesService;
import com.codeabovelab.dm.cluman.security.ProvidersAclService;
import com.codeabovelab.dm.cluman.security.SecuredType;
import com.codeabovelab.dm.cluman.ui.model.UiRole;
import com.codeabovelab.dm.cluman.ui.model.UiUser;
import com.codeabovelab.dm.cluman.users.UserRegistration;
import com.codeabovelab.dm.cluman.users.UsersStorage;
import com.codeabovelab.dm.cluman.validate.ExtendedAssert;
import com.codeabovelab.dm.common.security.*;
import com.codeabovelab.dm.common.security.acl.AclSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 */
@RestController
@RequestMapping(value = "/ui/api/", produces = APPLICATION_JSON_VALUE)
public class SecurityApi {

    @Autowired
    private UserIdentifiersDetailsService usersService;

    @Autowired
    private UsersStorage usersStorage;

    @Autowired
    private AuthoritiesService authoritiesService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AbstractAclService aclService;

    @Autowired
    private ProvidersAclService providersAclService;

    @RequestMapping(value = "/users/", method = RequestMethod.GET)
    public Collection<UiUser> getUsers() {
        Collection<ExtendedUserDetails> users = usersService.getUsers();
        return users.stream().map(UiUser::fromDetails).collect(Collectors.toList());
    }

    @RequestMapping(value = "/users/{user}", method = RequestMethod.GET)
    public UiUser getUser(@PathVariable("user") String username) {
        ExtendedUserDetails user = getUserDetails(username);
        return UiUser.fromDetails(user);
    }

    private ExtendedUserDetails getUserDetails(String username) {
        ExtendedUserDetails user;
        try {
            user = usersService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            user = null;
        }
        if(user == null) {
            throw new HttpException(HttpStatus.NOT_FOUND, "Can not find user with name: " + username);
        }
        return user;
    }

    @RequestMapping(value = "/users/{user}", method = RequestMethod.POST)
    public void setUser(@PathVariable("user") String username, @RequestBody UiUser user) {
        user.setUser(username);
        String password = user.getPassword();
        // we must encode password
        if(password != null && !UiUser.PWD_STUB.equals(password)) {
            String encodedPwd = passwordEncoder.encode(password);
            user.setPassword(encodedPwd);
        }
        usersStorage.update(username, (ur) -> {
            ExtendedUserDetailsImpl.Builder builder = ExtendedUserDetailsImpl.builder(ur.getDetails());
            user.toBuilder(builder);
            ur.setDetails(builder);
        });
    }

    @RequestMapping(value = "/users/{user}", method = RequestMethod.DELETE)
    public void deleteUser(@PathVariable("user") String username) {
        usersStorage.delete(username);
    }

    @RequestMapping(value = "/users-current", method = RequestMethod.GET)
    public UiUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        return UiUser.fromDetails(userDetails);
    }

    @RequestMapping(value = "/roles/", method = RequestMethod.GET)
    public Collection<UiRole> getGroups() {
        Collection<GrantedAuthority> authorities = authoritiesService.getAuthorities();
        return authorities.stream().map(UiRole::fromAuthority).collect(Collectors.toList());
    }

    @RequestMapping(value = "/users/{user}/roles/", method = RequestMethod.GET)
    public List<UiRole> getUserRoles(@PathVariable("user") String username) {
        ExtendedUserDetails details = getUserDetails(username);
        List<UiRole> roles = details.getAuthorities().stream().map(UiRole::fromAuthority).collect(Collectors.toList());
        roles.sort(null);
        return roles;
    }

    @RequestMapping(
      value = {
        "/users/{user}/roles/{role}",
        "/users/{user}/roles/{tenant}/{role}"
      },
      method = RequestMethod.DELETE)
    public void deleteUserRole(@PathVariable("user") String username,
                               @PathVariable("role") String role,
                               @PathVariable(value = "tenant", required = false) String tenant) {
        UserRegistration ur = usersStorage.get(username);
        ExtendedAssert.notFound(ur, "Can not find user: " + username);
        ur.update((r) -> {
            ExtendedUserDetailsImpl.Builder builder = ExtendedUserDetailsImpl.builder(ur.getDetails());
            boolean removed = builder.getAuthorities().removeIf(a -> a.getAuthority().equals(role) && Objects.equals(tenant, MultiTenancySupport.getTenant(a)));
            if (!removed) {
                throw new NotFoundException("Can not found specified role.");
            }
            r.setDetails(builder);
        });
    }

    @RequestMapping(
      value = {
        "/users/{user}/roles/{role}",
        "/users/{user}/roles/{tenant}/{role}"
      },
      method = RequestMethod.POST)
    public void addUserRole(@PathVariable("user") String username,
                            @PathVariable("role") String role,
                            @PathVariable(value = "tenant", required = false) String tenant) {
        GrantedAuthority authority = Authorities.fromName(role, tenant);
        // also we can check that this authority is exists
        UserRegistration ur = usersStorage.get(username);
        ExtendedAssert.notFound(ur, "Can not find user: " + username);
        ur.update((r) -> {
            ExtendedUserDetailsImpl.Builder builder = ExtendedUserDetailsImpl.builder(ur.getDetails());
            boolean added = builder.getAuthorities().add(authority);
            if(!added) {
                throw new HttpException(HttpStatus.NOT_MODIFIED, "User already has specified authority.");
            }
            r.setDetails(builder);
        });
    }

    @RequestMapping(path = "/acl/", method = RequestMethod.GET)
    public List<String> getSecuredTypes() {
        return Arrays.asList(SecuredType.values()).stream().map(SecuredType::name).collect(Collectors.toList());
    }

    @RequestMapping(path = "/acl/{type}/{id}", method = RequestMethod.GET)
    public AclSource getAcl(@PathVariable("type") String type, @PathVariable("id") String id) {
        SecuredType securedType = SecuredType.valueOf(type);
        ObjectIdentity oid = securedType.id(id);
        try {
            AclSource acl = aclService.getAclSource(oid);
            return acl;
        } catch (org.springframework.security.acls.model.NotFoundException e) {
            throw new NotFoundException(e);
        }
    }

    @RequestMapping(path = "/acl/{type}/{id}", method = RequestMethod.POST)
    public void setAcl(@PathVariable("type") String type, @PathVariable("id") String id, @RequestBody AclSource aclSource) {
        SecuredType securedType = SecuredType.valueOf(type);
        ObjectIdentity oid = securedType.id(id);
        try {
            providersAclService.updateAclSource(oid, as -> {
                //TODO now full update with skip all old data, but it wrong
                return aclSource;
            });
        } catch (org.springframework.security.acls.model.NotFoundException e) {
            throw new NotFoundException(e);
        }
    }
}
