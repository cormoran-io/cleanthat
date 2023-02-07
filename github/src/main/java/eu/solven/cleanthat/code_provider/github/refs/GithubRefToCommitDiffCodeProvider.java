/*
 * Copyright 2023 Benoit Lacelle - SOLVEN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.solven.cleanthat.code_provider.github.refs;

import eu.solven.cleanthat.codeprovider.ICodeProvider;
import eu.solven.cleanthat.codeprovider.IListOnlyModifiedFiles;
import java.nio.file.FileSystem;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;

/**
 * An {@link ICodeProvider} from a ref to a commit
 *
 * @author Benoit Lacelle
 */
public class GithubRefToCommitDiffCodeProvider extends AGithubDiffCodeProvider implements IListOnlyModifiedFiles {
	final GHRef base;
	final GHCommit head;

	public GithubRefToCommitDiffCodeProvider(FileSystem fs,
			String token,
			GHRepository baseRepository,
			GHRef base,
			GHCommit head) {
		super(fs, token, baseRepository);

		this.base = base;
		this.head = head;
	}

	@Override
	protected String getBaseId() {
		return base.getRef();
	}

	@Override
	protected String getHeadId() {
		return head.getSHA1();
	}

}
