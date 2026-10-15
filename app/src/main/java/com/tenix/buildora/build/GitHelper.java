package com.tenix.buildora.build;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;

/** Thin wrapper over JGit for clone / status / commit / pull / push. */
public final class GitHelper {
    private GitHelper() {}

    public static boolean isRepo(File root) {
        return new File(root, ".git").exists();
    }

    public static String repoName(String url) {
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
        int i = Math.max(u.lastIndexOf('/'), u.lastIndexOf(':'));
        String n = i >= 0 ? u.substring(i + 1) : u;
        return n.isEmpty() ? "repo" : n;
    }

    private static CredentialsProvider creds(String user, String token) {
        if (token == null || token.isEmpty()) return null;
        if (user == null || user.isEmpty()) return new UsernamePasswordCredentialsProvider(token, "");
        return new UsernamePasswordCredentialsProvider(user, token);
    }

    public static void clone(String url, File dir, String branch, String user, String token) throws Exception {
        CloneCommand c = Git.cloneRepository().setURI(url.trim()).setDirectory(dir);
        if (branch != null && !branch.trim().isEmpty()) c.setBranch(branch.trim());
        CredentialsProvider cp = creds(user, token);
        if (cp != null) c.setCredentialsProvider(cp);
        Git g = c.call();
        g.close();
    }

    public static String status(File root) throws Exception {
        try (Git g = Git.open(root)) {
            Status s = g.status().call();
            StringBuilder sb = new StringBuilder();
            sb.append("Branch: ").append(g.getRepository().getBranch()).append("\n\n");
            section(sb, "Added", s.getAdded());
            section(sb, "Changed", s.getChanged());
            section(sb, "Modified", s.getModified());
            section(sb, "Removed", s.getRemoved());
            section(sb, "Missing", s.getMissing());
            section(sb, "Untracked", s.getUntracked());
            section(sb, "Conflicting", s.getConflicting());
            if (s.isClean()) sb.append("Working tree clean.");
            return sb.toString().trim();
        }
    }

    private static void section(StringBuilder sb, String title, java.util.Set<String> files) {
        if (files == null || files.isEmpty()) return;
        sb.append(title).append(" (").append(files.size()).append("):\n");
        int n = 0;
        for (String f : files) {
            if (n++ >= 40) { sb.append("  …\n"); break; }
            sb.append("  ").append(f).append('\n');
        }
        sb.append('\n');
    }

    public static String commit(File root, String message, String name, String email) throws Exception {
        try (Git g = Git.open(root)) {
            g.add().addFilepattern(".").call();
            g.add().setUpdate(true).addFilepattern(".").call();
            String n = name == null || name.isEmpty() ? "Buildora" : name;
            String e = email == null || email.isEmpty() ? "buildora@localhost" : email;
            org.eclipse.jgit.revwalk.RevCommit rc = g.commit().setMessage(message).setAuthor(n, e).setCommitter(n, e).call();
            return "Committed " + rc.getId().abbreviate(8).name() + ": " + message;
        }
    }

    public static String pull(File root, String user, String token) throws Exception {
        try (Git g = Git.open(root)) {
            PullResult r = g.pull().setCredentialsProvider(creds(user, token)).call();
            return r.isSuccessful() ? "Pull successful." : "Pull finished with conflicts or problems:\n" + r;
        }
    }

    public static String push(File root, String user, String token) throws Exception {
        try (Git g = Git.open(root)) {
            StringBuilder sb = new StringBuilder();
            Iterable<PushResult> rs = g.push().setCredentialsProvider(creds(user, token)).call();
            for (PushResult pr : rs) {
                for (RemoteRefUpdate u : pr.getRemoteUpdates()) {
                    sb.append(u.getRemoteName()).append(": ").append(u.getStatus()).append('\n');
                }
            }
            return sb.length() == 0 ? "Pushed." : sb.toString().trim();
        }
    }
}
