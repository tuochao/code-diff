package com.dr.code.diff.util;

import com.dr.common.errorcode.BizCode;
import com.dr.common.exception.BizException;
import com.dr.common.log.LoggerUtil;
import com.dr.common.utils.file.FileUtil;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.antlr.runtime.tree.TreeParser;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;

/**
 * @ProjectName: code-diff-parent
 * @Package: com.dr.code.diff.util
 * @Description: java类作用描述
 * @Author: duanrui
 * @CreateDate: 2021/4/5 11:16
 * @Version: 1.0
 * <p>
 * Copyright: Copyright (c) 2021
 */
@Slf4j
public class GitRepoUtil {


    /**
     * 克隆代码到本地
     *
     * @param gitUrl
     * @param codePath
     * @param commitId
     * @return
     * @throws GitAPIException
     * @throws IOException
     */
    public static Git cloneRepository(String gitUrl, String codePath, String branch, String commitId, String gitUserName, String gitPassWord) {
        Git git = null;
        try {
            if (!checkGitWorkSpace(gitUrl, codePath))
            {
                LoggerUtil.info(log, "本地代码不存在，clone", gitUrl, codePath);
                git = Git.cloneRepository()
                        .setURI(gitUrl)
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitUserName, gitPassWord))
                        .setDirectory(new File(codePath))
                        .setBranch(branch)
                        .call();
                // 下载指定commitId/branch
//                git.checkout().setName(commitId).call();
            } else {
                LoggerUtil.info(log, "本地代码存在,直接使用", gitUrl, codePath);
                git = Git.open(new File(codePath));
                git.getRepository().getFullBranch();
                //判断是分支还是commitId，分支做更新，commitId无法改变用原有的
                if (git.getRepository().exactRef(Constants.HEAD).isSymbolic()) {
                    //更新代码
                    git.pull().setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitUserName, gitPassWord)).call();
                }
            }
            // git reset --hard to switch to specific commit
            git.reset().setRef(commitId).setMode(ResetCommand.ResetType.HARD).call();
        } catch (IOException | GitAPIException e) {
            if(e instanceof GitAPIException){
                throw new BizException(BizCode.GIT_AUTH_FAILED.getCode(), e.getMessage());
            }
            e.printStackTrace();
            throw new BizException(BizCode.GIT_OPERATED_FAIlED);
        }
        return git;
    }

    /**
     * 将代码转成树状
     *
     * @param repository
     * @param branchName
     * @return
     */
    public static AbstractTreeIterator prepareTreeParser(Repository repository, String branchName) {
        try {
            RevWalk walk = new RevWalk(repository);
            RevTree tree;
            if(null == repository.resolve(branchName)){
                throw new BizException(BizCode.PARSE_BRANCH_ERROR);
            }
            tree = walk.parseTree(repository.resolve(branchName));
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }




    /**
     * 判断工作目录是否存在，本来可以每次拉去代码时删除再拉取，但是这样代码多的化IO比较大，所以就代码可以复用
     *
     * @param codePath
     * @return
     */
    public static Boolean checkGitWorkSpace(String gitUrl, String codePath) throws IOException {
        Boolean isExist = Boolean.FALSE;
//        File repoGitDir = new File(codePath + "/.git");
//        File repoGitDir = new File(codePath + File.separator + ".git");

        File repoGitDir = new File(codePath + File.separator + ".git");
        if (!repoGitDir.exists()) {
            return isExist;
        }
        Git git = Git.open(new File(codePath));
        if (null == git) {
            return isExist;
        }
        Repository repository = git.getRepository();
        //解析本地代码，获取远程uri,是否是我们需要的git远程仓库
        String repoUrl = repository.getConfig().getString("remote", "origin", "url");
        if (gitUrl.equals(repoUrl)) {
            isExist = Boolean.TRUE;
        } else {
            LoggerUtil.info(log, "本地存在其他仓的代码，先删除");
            FileUtil.removeDir(new File(codePath));
        }
        return isExist;
    }


    /**
     * 取远程代码本地存储路径
     *
     * @param repoUrl
     * @param localBaseRepoDir
     * @param version
     * @return
     */
    public static String getLocalDir(String repoUrl, String localBaseRepoDir, String version) {
        StringBuilder localDir = new StringBuilder(localBaseRepoDir);
        if (Strings.isNullOrEmpty(repoUrl)) {
            return "";
        }
//        localDir.append("/");
        localDir.append(File.separator);
        String repoName = Splitter.on("/")
                .splitToStream(repoUrl).reduce((first, second) -> second)
                .map(e -> Splitter.on(".").splitToStream(e).findFirst().get()).get();
        localDir.append(repoName);
        if(!StringUtils.isEmpty(version)){
//            localDir.append("/");
            localDir.append(File.separator);
            localDir.append(version);
        }
        return localDir.toString();
    }

}
