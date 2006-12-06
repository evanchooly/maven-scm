package org.apache.maven.scm.provider.svn.svnexe.command.tag;

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

import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.command.tag.AbstractTagCommand;
import org.apache.maven.scm.command.tag.TagScmResult;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.svn.SvnCommandUtils;
import org.apache.maven.scm.provider.svn.SvnTagBranchUtils;
import org.apache.maven.scm.provider.svn.command.SvnCommand;
import org.apache.maven.scm.provider.svn.repository.SvnScmProviderRepository;
import org.apache.maven.scm.provider.svn.svnexe.command.SvnCommandLineUtils;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 * @todo since this is just a copy, use that instead.
 */
public class SvnTagCommand
    extends AbstractTagCommand
    implements SvnCommand
{
    public ScmResult executeTagCommand( ScmProviderRepository repo, ScmFileSet fileSet, String tag )
        throws ScmException
    {
        if ( tag == null || StringUtils.isEmpty( tag.trim() ) )
        {
            throw new ScmException( "tag must be specified" );
        }

        if ( fileSet.getFiles().length != 0 )
        {
            throw new ScmException( "This provider doesn't support tagging subsets of a directory" );
        }

        SvnScmProviderRepository repository = (SvnScmProviderRepository) repo;

        File messageFile = FileUtils.createTempFile( "maven-scm-", ".commit", null );

        try
        {
            // TODO: should message be customisable?
            FileUtils.fileWrite( messageFile.getAbsolutePath(), "[maven-scm] copy for tag " + tag );
        }
        catch ( IOException ex )
        {
            return new TagScmResult( null,
                                     "Error while making a temporary file for the commit message: " + ex.getMessage(),
                                     null, false );
        }

        Commandline cl = createCommandLine( repository, fileSet.getBasedir(), tag, messageFile );

        CommandLineUtils.StringStreamConsumer stdout = new CommandLineUtils.StringStreamConsumer();

        CommandLineUtils.StringStreamConsumer stderr = new CommandLineUtils.StringStreamConsumer();

        getLogger().info( "Executing: " + SvnCommandLineUtils.cryptPassword( cl ) );
        getLogger().info( "Working directory: " + cl.getWorkingDirectory().getAbsolutePath() );

        int exitCode;

        try
        {
            exitCode = SvnCommandLineUtils.execute( cl, stdout, stderr, getLogger() );
        }
        catch ( CommandLineException ex )
        {
            throw new ScmException( "Error while executing command.", ex );
        }

        if ( exitCode != 0 )
        {
            // TODO: Improve this error message
            return new TagScmResult( cl.toString(), "The svn tag command failed.", stderr.getOutput(), false );
        }

        List fileList = new ArrayList();

        List files = null;

        try
        {
            files = FileUtils.getFiles( fileSet.getBasedir(), "**", "**/.svn/**", false );
        }
        catch ( IOException e )
        {
            throw new ScmException( "Error while executing command.", e );
        }

        for ( Iterator i = files.iterator(); i.hasNext(); )
        {
            File f = (File) i.next();

            fileList.add( new ScmFile( f.getPath(), ScmFileStatus.TAGGED ) );
        }

        return new TagScmResult( cl.toString(), fileList );
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public static Commandline createCommandLine( SvnScmProviderRepository repository, File workingDirectory, String tag,
                                                 File messageFile )
    {
        Commandline cl = SvnCommandLineUtils.getBaseSvnCommandLine( workingDirectory, repository );

        cl.createArgument().setValue( "copy" );

        cl.createArgument().setValue( "--file" );

        cl.createArgument().setValue( messageFile.getAbsolutePath() );

        cl.createArgument().setValue( "." );

        // Note: this currently assumes you have the tag base checked out too
        String tagUrl = SvnTagBranchUtils.resolveTagUrl( repository, tag );
        cl.createArgument().setValue( SvnCommandUtils.fixUrl( tagUrl, repository.getUser() ) );

        return cl;
    }
}
