package com.sitasp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class SingleCommitUpdaterTest {
    private static final Logger log = LogManager.getLogger(SingleCommitUpdaterTest.class);

    private Path tempDir;
    private Repository repository;
    private Git git;
    private RevCommit initialCommit;

    @BeforeEach
    public void setUp() throws IOException, GitAPIException {
        tempDir = Files.createTempDirectory("test-repo");
        // Initialize the Git repository
        git = Git.init().setDirectory(tempDir.toFile()).call();
        repository = git.getRepository();

        // Verify that the .git directory exists
        File gitDir = new File(tempDir.toFile(), ".git");
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            throw new IOException("Failed to initialize Git repository at " + tempDir);
        }

        File myFile = new File(tempDir.toFile(), "initial.txt");
        if (myFile.createNewFile()) {
            Files.writeString(myFile.toPath(), "Initial content");
        }
        git.add().addFilepattern("initial.txt").call();
        initialCommit = git.commit().setMessage("Initial commit").call();
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
        Files.walk(tempDir)
                .sorted((path1, path2) -> path2.compareTo(path1))  // Delete files first (reverse order)
                .map(Path::toFile)
                .forEach(File::delete);

        // Delete the temp directory itself
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void testUpdateCommitDatesSingle() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now();

        assertDoesNotThrow(() -> CommitUpdater.updateCommitDatesSingle(tempDir.toString(), startDate, endDate));
    }

    @Test
    public void testUpdateCommitDatesMultiple() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(1);
        LocalDateTime endDate = LocalDateTime.now();

        assertDoesNotThrow(() -> CommitUpdater.updateCommitDatesMultiple(tempDir.toString(), startDate, endDate));
    }

    @Test
    public void whenSingleCommit_checkCommitSizeShouldBeOne(){
        LocalDateTime startDate = LocalDateTime.now().minusDays(90);
        LocalDateTime endDate = startDate.plusDays(7);

        CommitUpdater.updateCommitDatesSingle(tempDir.toString(), startDate, endDate);
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
        LocalDateTime startDate = LocalDateTime.now().minusDays(90);
        LocalDateTime endDate = startDate.plusDays(7);

        CommitUpdater.updateCommitDatesSingle(tempDir.toString(), startDate, endDate);
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
}