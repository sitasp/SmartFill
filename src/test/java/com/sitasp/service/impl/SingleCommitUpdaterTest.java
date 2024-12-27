package com.sitasp.service.impl;

import com.sitasp.objects.CommitUpdateRequest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;


class SingleCommitUpdaterTest {

    private CommitUpdateRequest commitUpdateRequest;
    private RevCommit initialCommit;
    private Git git;
    private Repository repository;
    private final SingleCommitUpdater singleCommitUpdater = new SingleCommitUpdater();

    @BeforeEach
    public void setUp() throws IOException, GitAPIException {
        Path tempDir = Files.createTempDirectory("test-repo");

        // Initialize the Git repository
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repository = git.getRepository();
        setupRepo(tempDir);
        initCommit(tempDir);

        LocalDateTime startDate = LocalDateTime.now().minusDays(90);
        LocalDateTime endDate = startDate.plusDays(7);
        commitUpdateRequest = new CommitUpdateRequest();
        commitUpdateRequest.startDate(startDate);
        commitUpdateRequest.endDate(endDate);
        commitUpdateRequest.repoPath(tempDir.toString());
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (repository != null) {
            repository.close();
        }
        if (git != null) {
            git.close();
        }
        // Recursively delete the files before deleting the directory
        Files.walk(Paths.get(commitUpdateRequest.repoPath()))
                .sorted((path1, path2) -> path2.compareTo(path1))  // Delete files first (reverse order)
                .map(Path::toFile)
                .forEach(File::delete);

        // Delete the temp directory itself
        Files.deleteIfExists(Paths.get(commitUpdateRequest.repoPath()));
    }

    @Test
    public void testUpdateCommitDatesSingle() {
        assertDoesNotThrow(() -> singleCommitUpdater.updateCommit(commitUpdateRequest));
    }

    @Test
    public void whenSingleCommit_checkCommitSizeShouldBeOne(){
        singleCommitUpdater.updateCommit(commitUpdateRequest);
        try{
            Iterable<RevCommit> commits = git.log().call();
            // find size of commits
            long commitCount = 0;
            for (RevCommit commit : commits) {
                commitCount++;
            }
            assertEquals(1, commitCount);
        } catch (GitAPIException e) {
            e.printStackTrace();
            // Fail the test if an exception occurs
            fail();
        }
    }

    @Test
    public void whenSingleCommit_checkCommitPerson_shouldBeSameWithInitialCommit(){
        singleCommitUpdater.updateCommit(commitUpdateRequest);
        try{
            Iterable<RevCommit> commits = git.log().call();

            RevCommit singleCommit = commits.iterator().next();
            assertEquals(initialCommit.getAuthorIdent().getName(), singleCommit.getAuthorIdent().getName());
            assertEquals(initialCommit.getAuthorIdent().getEmailAddress(), singleCommit.getAuthorIdent().getEmailAddress());
            assertEquals(initialCommit.getCommitterIdent().getName(), singleCommit.getCommitterIdent().getName());
            assertEquals(initialCommit.getCommitterIdent().getEmailAddress(), singleCommit.getCommitterIdent().getEmailAddress());
        } catch (GitAPIException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void whenSingleCommit_checkCommitId_shouldBeUpdated(){
        singleCommitUpdater.updateCommit(commitUpdateRequest);
        try{
            Iterable<RevCommit> commits = git.log().call();

            RevCommit singleCommit = commits.iterator().next();
            assertNotEquals(singleCommit.getId().getName(), initialCommit.getId().getName());
        } catch (GitAPIException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void whenSingleCommit_checkNewCommit_isItUnreferenced() {
        System.out.println("Starting test...");
        singleCommitUpdater.updateCommit(commitUpdateRequest);
        System.out.println("Completed commit date update");

        try (Git git = new Git(repository)) {
            // Print all commits
            Iterable<RevCommit> commits = git.log().all().call();
            System.out.println("\nAll commits in repository:");
            for (RevCommit commit : commits) {
                System.out.println("Commit: " + commit.getName() +
                        " | Date: " + commit.getAuthorIdent().getWhen() +
                        " | Message: " + commit.getShortMessage());
            }

            // Check for unreferenced commits
            ObjectWalk ow = new ObjectWalk(repository);
            System.out.println("\nChecking for unreferenced commits...");

            for (Ref ref : repository.getRefDatabase().getRefs()) {
                System.out.println("Found ref: " + ref.getName());
                ow.markUninteresting(ow.parseAny(ref.getObjectId()));
            }

            RevCommit commit;
            while ((commit = ow.next()) != null) {
                fail("Found unreferenced commit: " + commit.getName());
            }
            System.out.println("No unreferenced commits found");

        } catch (Exception e) {
            e.printStackTrace();
            fail("Test failed with exception: " + e.getMessage());
        }
    }

    @Test
    public void whenSingleCommit_checkCommitDate_shouldBeUpdated(){
        singleCommitUpdater.updateCommit(commitUpdateRequest);
        try{
            Iterable<RevCommit> commits = git.log().call();

            RevCommit singleCommit = commits.iterator().next();

            System.out.println("Author Indent instant: " + singleCommit.getAuthorIdent().getWhenAsInstant());
            System.out.println("Committer Indent instant: " + singleCommit.getCommitterIdent().getWhenAsInstant());
            // startdate println
            System.out.println("Start date: " + commitUpdateRequest.startDate().toInstant(ZoneOffset.UTC));

            assertNotEquals(singleCommit.getAuthorIdent().getWhenAsInstant(), initialCommit.getAuthorIdent().getWhenAsInstant());
            assertTrue(singleCommit.getAuthorIdent().getWhenAsInstant().isAfter(commitUpdateRequest.startDate().minusDays(1).toInstant(ZoneOffset.UTC)));
            assertTrue(singleCommit.getCommitterIdent().getWhenAsInstant().isAfter(commitUpdateRequest.startDate().minusDays(1).toInstant(ZoneOffset.UTC)));
            assertTrue(singleCommit.getAuthorIdent().getWhenAsInstant().isBefore(commitUpdateRequest.endDate().plusDays(1).toInstant(ZoneOffset.UTC)));
            assertTrue(singleCommit.getCommitterIdent().getWhenAsInstant().isBefore(commitUpdateRequest.endDate().plusDays(1).toInstant(ZoneOffset.UTC)));
        } catch (GitAPIException e) {
            e.printStackTrace();
            fail();
        }
    }

    private void initCommit(Path tempDir) throws IOException, GitAPIException {
        File myFile = new File(tempDir.toFile(), "initial.txt");
        if (myFile.createNewFile()) {
            Files.writeString(myFile.toPath(), "Initial content");
        }
        git.add().addFilepattern("initial.txt").call();
        initialCommit = git.commit().setMessage("Initial commit").call();
    }

    private void setupRepo(Path tempDir) throws IOException {
        // Verify that the .git directory exists
        File gitDir = new File(tempDir.toFile(), ".git");
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            throw new IOException("Failed to initialize Git repository at " + tempDir);
        }
    }


}