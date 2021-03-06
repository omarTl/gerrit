// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Branch;
import org.junit.Before;
import org.junit.Test;

public class DeleteBranchIT extends AbstractDaemonTest {

  private Branch.NameKey branch;

  @Before
  public void setUp() throws Exception {
    project = createProject(name("p"));
    branch = new Branch.NameKey(project, "test");
    branch().create(new BranchInput());
  }

  @Test
  public void deleteBranch_Forbidden() throws Exception {
    setApiUser(user);
    assertDeleteForbidden();
  }

  @Test
  public void deleteBranchByAdmin() throws Exception {
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByProjectOwner() throws Exception {
    grantOwner();
    setApiUser(user);
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByAdminForcePushBlocked() throws Exception {
    blockForcePush();
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByProjectOwnerForcePushBlocked_Forbidden() throws Exception {
    grantOwner();
    blockForcePush();
    setApiUser(user);
    assertDeleteForbidden();
  }

  @Test
  public void deleteBranchByUserWithForcePushPermission() throws Exception {
    grantForcePush();
    setApiUser(user);
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByUserWithDeletePermission() throws Exception {
    grantDelete();
    setApiUser(user);
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByRestWithoutRefsHeadsPrefix() throws Exception {
    grantDelete();
    String ref = branch.getShortName();
    assertThat(ref).doesNotMatch(R_HEADS);
    assertDeleteByRestSucceeds(ref);
  }

  @Test
  public void deleteBranchByRestWithEncodedFullName() throws Exception {
    grantDelete();
    assertDeleteByRestSucceeds(Url.encode(branch.get()));
  }

  @Test
  public void deleteBranchByRestFailsWithUnencodedFullName() throws Exception {
    grantDelete();
    RestResponse r =
        userRestSession.delete("/projects/" + project.get() + "/branches/" + branch.get());
    r.assertNotFound();
    branch().get();
  }

  private void blockForcePush() throws Exception {
    block("refs/heads/*", Permission.PUSH, ANONYMOUS_USERS).setForce(true);
  }

  private void grantForcePush() throws Exception {
    grant(project, "refs/heads/*", Permission.PUSH, true, ANONYMOUS_USERS);
  }

  private void grantDelete() throws Exception {
    allow("refs/*", Permission.DELETE, ANONYMOUS_USERS);
  }

  private void grantOwner() throws Exception {
    allow("refs/*", Permission.OWNER, REGISTERED_USERS);
  }

  private BranchApi branch() throws Exception {
    return gApi.projects().name(branch.getParentKey().get()).branch(branch.get());
  }

  private void assertDeleteByRestSucceeds(String ref) throws Exception {
    RestResponse r = userRestSession.delete("/projects/" + project.get() + "/branches/" + ref);
    r.assertNoContent();
    exception.expect(ResourceNotFoundException.class);
    branch().get();
  }

  private void assertDeleteSucceeds() throws Exception {
    String branchRev = branch().get().revision;
    branch().delete();
    eventRecorder.assertRefUpdatedEvents(
        project.get(), branch.get(), null, branchRev, branchRev, null);
    exception.expect(ResourceNotFoundException.class);
    branch().get();
  }

  private void assertDeleteForbidden() throws Exception {
    exception.expect(AuthException.class);
    exception.expectMessage("delete not permitted");
    branch().delete();
  }
}
