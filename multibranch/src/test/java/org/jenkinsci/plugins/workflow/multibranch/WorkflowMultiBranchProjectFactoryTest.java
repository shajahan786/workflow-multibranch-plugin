/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.multibranch;

import java.io.File;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMSource;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.steps.scm.GitSampleRepoRule;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class WorkflowMultiBranchProjectFactoryTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule sampleRepo2 = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule sampleRepo3 = new GitSampleRepoRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void smokes() throws Exception {
        File clones = tmp.newFolder();
        sampleRepo1.init();
        sampleRepo1.write(WorkflowMultiBranchProjectFactory.JENKINSFILE, "echo 'ran one'");
        sampleRepo1.git("add", WorkflowMultiBranchProjectFactory.JENKINSFILE);
        sampleRepo1.git("commit", "--all", "--message=flow");
        sampleRepo1.git("clone", ".", new File(clones, "one").getAbsolutePath());
        sampleRepo3.init(); // but do not write JENKINSFILE, so should be ignored
        sampleRepo3.git("clone", ".", new File(clones, "three").getAbsolutePath());
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        top.getNavigators().add(new GitDirectorySCMNavigator(clones.getAbsolutePath()));
        // Make sure we created one multibranch projects:
        r.waitUntilNoActivity();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(1, top.getItems().size());
        MultiBranchProject<?,?> one = top.getItem("one");
        assertThat(one, is(instanceOf(WorkflowMultiBranchProject.class)));
        // Check that it has Git configured:
        List<SCMSource> sources = one.getSCMSources();
        assertEquals(1, sources.size());
        assertEquals("GitSCMSource", sources.get(0).getClass().getSimpleName());
        // Check that the master branch project works:
        WorkflowJob p = findBranchProject((WorkflowMultiBranchProject) one, "master");
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("ran one", b1);
        // Then add a second checkout and reindex:
        sampleRepo2.init();
        sampleRepo2.write(WorkflowMultiBranchProjectFactory.JENKINSFILE, "echo 'ran two'");
        sampleRepo2.git("add", WorkflowMultiBranchProjectFactory.JENKINSFILE);
        sampleRepo2.git("commit", "--all", "--message=flow");
        sampleRepo2.git("clone", ".", new File(clones, "two").getAbsolutePath());
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(2, top.getItems().size());
        // Same for another one:
        MultiBranchProject<?,?> two = top.getItem("two");
        assertThat(two, is(instanceOf(WorkflowMultiBranchProject.class)));
        p = findBranchProject((WorkflowMultiBranchProject) two, "master");
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("ran two", b1);
    }

    // adapted from WorkflowMultiBranchProjectTest
    private @Nonnull WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        r.waitUntilNoActivity();
        WorkflowJob p = mp.getItem(name);
        if (p == null) {
            mp.getIndexing().writeWholeLogTo(System.out);
            fail(name + " project not found");
        }
        return p;
    }

}
