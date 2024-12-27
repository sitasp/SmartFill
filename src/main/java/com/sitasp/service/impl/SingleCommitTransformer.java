package com.sitasp.service.impl;

import com.sitasp.objects.CommitTransformRequest;
import com.sitasp.service.CommitTransform;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

@Slf4j
public class SingleCommitTransformer implements CommitTransform {
    @Override
    public void transformCommit(CommitTransformRequest commitRequest) {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try {
            // Open repository r1
            Repository r1 = builder.setGitDir(new File(commitRequest.repoPath() + "/.git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();

            try (Git gitR1 = new Git(r1)) {
                // Convert LocalDateTime to Instant
                Instant startInstant = commitRequest.startDate().toInstant(ZoneOffset.UTC);
                Instant endInstant = commitRequest.endDate().toInstant(ZoneOffset.UTC);

                // Get all commits and count them
                Iterable<RevCommit> commits = gitR1.log().call();
                List<RevCommit> commitList = new ArrayList<>();
                for (RevCommit commit : commits) {
                    commitList.add(commit);
                }

                long commitCount = commitList.size();
                long timeIncrement = (commitCount > 1) ?
                        (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / (commitCount - 1) : 0;

                // Process commits from oldest to newest
                Collections.reverse(commitList);

                // Track mapping of old commit IDs to new commit IDs
                Map<ObjectId, ObjectId> oldToNewCommits = new HashMap<>();

                Instant currentInstant = startInstant;
                ObjectId lastNewCommitId = null;

                for (RevCommit commit : commitList) {
                    log.info("Rewriting commit: {}", commit.getName());

                    // Adjust the commit date
                    Date newAuthorDate = Date.from(currentInstant);
                    PersonIdent originalAuthor = commit.getAuthorIdent();
                    PersonIdent originalCommitter = commit.getCommitterIdent();
                    PersonIdent newAuthor = new PersonIdent(
                            originalAuthor.getName(),
                            originalAuthor.getEmailAddress(),
                            newAuthorDate,
                            originalAuthor.getTimeZone()
                    );
                    PersonIdent newCommitter = new PersonIdent(
                            originalCommitter.getName(),
                            originalCommitter.getEmailAddress(),
                            newAuthorDate,
                            originalCommitter.getTimeZone()
                    );

                    // Build a new commit with updated dates
                    CommitBuilder commitBuilder = new CommitBuilder();
                    commitBuilder.setTreeId(commit.getTree());
                    commitBuilder.setAuthor(newAuthor);
                    commitBuilder.setCommitter(newCommitter);
                    commitBuilder.setMessage(commit.getFullMessage());

                    // Update parent references to use new commit IDs
                    if (commit.getParentCount() > 0) {
                        ObjectId[] parentIds = new ObjectId[commit.getParentCount()];
                        for (int i = 0; i < commit.getParentCount(); i++) {
                            ObjectId oldParent = commit.getParent(i).getId();
                            // Use new commit ID if available, otherwise use old ID
                            parentIds[i] = oldToNewCommits.getOrDefault(oldParent, oldParent);
                        }
                        commitBuilder.setParentIds(parentIds);
                    }

                    // Create new commit and store mapping
                    ObjectId newCommitId = r1.getObjectDatabase().newInserter().insert(commitBuilder);
                    oldToNewCommits.put(commit.getId(), newCommitId);
                    lastNewCommitId = newCommitId;

                    log.info("New commit created: {}", newCommitId.getName());

                    // Increment the date for the next commit
                    currentInstant = currentInstant.plusMillis(timeIncrement);
                }

                // Update branch reference to point to the last new commit
                if (lastNewCommitId != null) {
                    String currentBranch = r1.getFullBranch();
                    log.info("Updating the current branch: {}", currentBranch);
                    RefUpdate refUpdate = r1.updateRef(currentBranch);
                    refUpdate.setNewObjectId(lastNewCommitId);
                    refUpdate.setForceUpdate(true);
                    RefUpdate.Result result = refUpdate.update();
                    log.info("Branch update result: {}", result);
                }
            }
        } catch (IOException | GitAPIException e) {
            log.error("Error updating commit dates", e);
            e.printStackTrace();
        }
    }
}
