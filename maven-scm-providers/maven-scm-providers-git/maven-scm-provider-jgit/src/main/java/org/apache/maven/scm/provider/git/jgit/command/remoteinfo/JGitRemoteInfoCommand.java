package org.apache.maven.scm.provider.git.jgit.command.remoteinfo;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.scm.CommandParameters;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.command.remoteinfo.AbstractRemoteInfoCommand;
import org.apache.maven.scm.command.remoteinfo.RemoteInfoScmResult;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.git.command.GitCommand;
import org.apache.maven.scm.provider.git.jgit.command.JGitTransportConfigCallback;
import org.apache.maven.scm.provider.git.jgit.command.JGitUtils;
import org.apache.maven.scm.provider.git.jgit.command.ScmProviderAwareSshdSessionFactory;
import org.apache.maven.scm.provider.git.repository.GitScmProviderRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author Dominik Bartholdi (imod)
 * @since 1.9
 */
public class JGitRemoteInfoCommand
    extends AbstractRemoteInfoCommand
    implements GitCommand
{

    private BiFunction<GitScmProviderRepository, Logger, ScmProviderAwareSshdSessionFactory> sshSessionFactorySupplier;

    public JGitRemoteInfoCommand()
    {
        sshSessionFactorySupplier = ScmProviderAwareSshdSessionFactory::new;
    }

    public void setSshSessionFactorySupplier(
        BiFunction<GitScmProviderRepository, Logger, ScmProviderAwareSshdSessionFactory> sshSessionFactorySupplier )
    {
        this.sshSessionFactorySupplier = sshSessionFactorySupplier;
    }

    @Override
    public RemoteInfoScmResult executeRemoteInfoCommand( ScmProviderRepository repository, ScmFileSet fileSet,
                                                         CommandParameters parameters )
        throws ScmException
    {

        GitScmProviderRepository repo = (GitScmProviderRepository) repository;
        Git git = null;
        try
        {
            git = JGitUtils.openRepo( fileSet.getBasedir() );
            CredentialsProvider credentials = JGitUtils.getCredentials( repo );

            LsRemoteCommand lsCommand =
                git.lsRemote().setRemote( repo.getPushUrl() ).setCredentialsProvider( credentials )
                        .setTransportConfigCallback(
                            new JGitTransportConfigCallback( sshSessionFactorySupplier.apply( repo, logger ) )
                        );

            Map<String, String> tag = new HashMap<>();
            Collection<Ref> allTags = lsCommand.setHeads( false ).setTags( true ).call();
            for ( Ref ref : allTags )
            {
                tag.put( Repository.shortenRefName( ref.getName() ), ref.getObjectId().name() );
            }

            Map<String, String> heads = new HashMap<>();
            Collection<Ref> allHeads = lsCommand.setHeads( true ).setTags( false ).call();
            for ( Ref ref : allHeads )
            {
                heads.put( Repository.shortenRefName( ref.getName() ), ref.getObjectId().name() );
            }

            return new RemoteInfoScmResult( "JGit remoteinfo", heads, tag );
        }
        catch ( Exception e )
        {
            throw new ScmException( "JGit remoteinfo failure!", e );
        }
        finally
        {
            JGitUtils.closeRepo( git );
        }
    }
}
