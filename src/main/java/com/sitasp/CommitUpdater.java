package com.sitasp;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

@Slf4j
public class CommitUpdater {
    private static final Logger LOGGER = LogManager.getLogger(CommitUpdater.class.getName());

    public static void updateCommitDatesSingle(String r1Path, LocalDateTime d1, LocalDateTime d2) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try {
            // Open repository r1
            Repository r1 = builder.setGitDir(new File(r1Path + "/.git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();

            try (Git gitR1 = new Git(r1)) {
                // Convert LocalDateTime to Instant
                Instant startInstant = d1.toInstant(ZoneOffset.UTC);
                Instant endInstant = d2.toInstant(ZoneOffset.UTC);

                // Iterate through the commits in r1
                Iterable<RevCommit> commits = gitR1.log().call();

                Instant currentInstant = startInstant;
                long commitCount = gitR1.log().call().spliterator().getExactSizeIfKnown();
                long timeIncrement = (commitCount > 1) ? (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / commitCount : 0;

                RevWalk revWalk = new RevWalk(r1);
                for (RevCommit commit : commits) {
                    LOGGER.info("Rewriting commit: " + commit.getName());

                    // Adjust the commit date
                    Date newAuthorDate = Date.from(currentInstant);
                    PersonIdent originalAuthor = commit.getAuthorIdent();
                    PersonIdent originalCommitter = commit.getCommitterIdent();
                    PersonIdent newAuthor = new PersonIdent(originalAuthor.getName(), originalAuthor.getEmailAddress(), newAuthorDate, originalAuthor.getTimeZone());
                    PersonIdent newCommitter = new PersonIdent(originalCommitter.getName(), originalCommitter.getEmailAddress(), newAuthorDate, originalCommitter.getTimeZone());

                    // Build a new commit with updated dates
                    CommitBuilder commitBuilder = new CommitBuilder();
                    commitBuilder.setTreeId(commit.getTree());
                    commitBuilder.setAuthor(newAuthor);
                    commitBuilder.setCommitter(newCommitter);
                    commitBuilder.setMessage(commit.getFullMessage());

                    if (commit.getParentCount() > 0) {
                        ObjectId[] parentIds = new ObjectId[commit.getParentCount()];
                        for (int i = 0; i < commit.getParentCount(); i++) {
                            parentIds[i] = commit.getParent(i);
                        }
                        commitBuilder.setParentIds(parentIds);
                    }

                    ObjectId newCommitId = r1.getObjectDatabase().newInserter().insert(commitBuilder);
                    LOGGER.info("New commit created: {}", newCommitId.getName());

                    // Increment the date for the next commit
                    currentInstant = currentInstant.plusMillis(timeIncrement);
                }

                revWalk.close();
            }
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }

    public static void updateCommitDatesMultiple(String r1Path, LocalDateTime d1, LocalDateTime d2) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try {
            // Open repository r1
            Repository r1 = builder.setGitDir(new File(r1Path + "/.git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();

            try (Git gitR1 = new Git(r1)) {
                // Convert LocalDateTime to Instant
                Instant startInstant = d1.toInstant(ZoneOffset.UTC);
                Instant endInstant = d2.toInstant(ZoneOffset.UTC);

                // Iterate through the commits in r1
                Iterable<RevCommit> commits = gitR1.log().call();

                Instant currentInstant = startInstant;
                long commitCount = gitR1.log().call().spliterator().getExactSizeIfKnown();
                long timeIncrement = (commitCount > 1) ? (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / commitCount : 0;

                RevWalk revWalk = new RevWalk(r1);
                for (RevCommit commit : commits) {
                    LOGGER.info("Splitting commit: {}", commit.getName());

                    TreeWalk treeWalk = new TreeWalk(r1);
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);

                    while (treeWalk.next()) {
                        String filePath = treeWalk.getPathString();
                        LOGGER.info("Processing file: {}", filePath);

                        // Adjust the commit date
                        Date newAuthorDate = Date.from(currentInstant);
                        PersonIdent originalAuthor = commit.getAuthorIdent();
                        PersonIdent originalCommitter = commit.getCommitterIdent();
                        PersonIdent newAuthor = new PersonIdent(originalAuthor.getName(), originalAuthor.getEmailAddress(), newAuthorDate, originalAuthor.getTimeZone());
                        PersonIdent newCommitter = new PersonIdent(originalCommitter.getName(), originalCommitter.getEmailAddress(), newAuthorDate, originalCommitter.getTimeZone());

                        // Trim the commit message if it's too long
                        String originalMessage = commit.getFullMessage();
                        String shortMessage = originalMessage.length() > 50 ? originalMessage.substring(0, 50) + "..." : originalMessage;

                        // Build a new commit with only this file
                        CommitBuilder commitBuilder = new CommitBuilder();
                        commitBuilder.setTreeId(treeWalk.getObjectId(0));
                        commitBuilder.setAuthor(newAuthor);
                        commitBuilder.setCommitter(newCommitter);
                        commitBuilder.setMessage(commit.getName().substring(0, 7) + ": Update " + filePath + " - " + shortMessage);

                        if (commit.getParentCount() > 0) {
                            ObjectId[] parentIds = new ObjectId[commit.getParentCount()];
                            for (int i = 0; i < commit.getParentCount(); i++) {
                                parentIds[i] = commit.getParent(i);
                            }
                            commitBuilder.setParentIds(parentIds);
                        }

                        ObjectId newCommitId = r1.getObjectDatabase().newInserter().insert(commitBuilder);
                        LOGGER.info("New commit created: {}", newCommitId.getName());

                        // Increment the date for the next commit
                        currentInstant = currentInstant.plusMillis(timeIncrement);
                    }

                    treeWalk.close();
                }

                revWalk.close();
            }
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }
}
