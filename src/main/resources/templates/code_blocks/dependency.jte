@import net.derfruhling.spacemaven.Builtin
@import net.derfruhling.spacemaven.SpecRef
@import java.util.Objects

@param SpecRef spec

<div>

    @if(spec.getRepository().equals("gradle-plugins"))
        <div class="code-block">
            <div class="line comment">// settings.gradle</div>
            <div class="line"><code class="keyword">pluginManagement</code> <code>{</code></div>
            <div class="indent">
                <div class="line"><code class="keyword">repositories</code> <code>{</code></div>
                <div class="indent">
                    <div class="line"><code class="keyword">maven</code> <code>{</code></div>
                    <div class="indent">
                        <div class="line"><code class="keyword">name</code> <code class="string">'spacemaven+${spec.getRepository()}'</code></div>
                        <div class="line"><code class="keyword">url</code> <code class="string">'${spec.getRepositoryUrl()}'</code></div>
                    </div>
                    <div class="line"><code>}</code></div>
                </div>
                <div class="line"><code>}</code></div>
            </div>
            <div class="line"><code>}</code></div>
            <div class="line"></div>
            <div class="line comment">// build.gradle</div>
            <div class="line"><code class="keyword">plugins</code> <code>{</code></div>
            <div class="indent">
                <div class="line">
                    <code class="keyword">id</code>
                    <code class="string">'${spec.getGroupId()}'</code>
                    <code class="keyword">version</code>
                    <code class="string">'${spec.getVersion()}'</code>
                </div>
            </div>
            <div class="line"><code>}</code></div>
        </div>
    @else
        <div class="code-block">
            @if (spec.getRepository().equals("tools"))
                <code class="line comment">// settings.gradle</code>
                <div class="line"><code class="keyword">pluginManagement</code> <code>{</code></div>
                <div class="indent">
                    <div class="line"><code class="keyword">repositories</code> <code>{</code></div>
                    <div class="indent">
                        <div class="line"><code class="keyword">maven</code> <code>{</code></div>
                        <div class="indent">
                            <div class="line"><code class="keyword">name</code> <code class="string">'spacemaven+gradle-plugins'</code></div>
                            <div class="line"><code class="keyword">url</code> <code class="string">'${Builtin.repositoryUrlFor("gradle-plugins")}'</code></div>
                        </div>
                        <div class="line"><code>}</code></div>
                    </div>
                    <div class="line"><code>}</code></div>
                </div>
                <div class="line"><code>}</code></div>
                <div class="line"></div>
            @endif
            <div class="line comment">// build.gradle</div>
            @if (spec.getRepository() == "tools")
                <div class="line"><code class="keyword">plugins</code> <code>{</code></div>
                <div class="indent">
                    <div class="line">
                        <code class="keyword">id</code>
                        <code class="string">'net.derfruhling.spacegradle.tools'</code>
                        <code class="keyword">version</code>
                        <code class="string">'${Builtin.getLatestVersionOf("net.derfruhling.spacegradle.tools:net.derfruhling.spacegradle.tools.gradle.plugin")}'</code>
                    </div>
                </div>
                <div class="line"><code>}</code></div>
                <div class="line"></div>
            @endif
            <div class="line"><code class="keyword">repositories</code> <code>{</code></div>
            <div class="indent">
                <div class="line"><code class="keyword">maven</code> <code>{</code></div>
                <div class="indent">
                    <div class="line"><code class="keyword">name</code> <code class="string">'spacemaven+${spec.getRepository()}'</code></div>
                    <div class="line"><code class="keyword">url</code> <code class="string">'${spec.getRepositoryUrl()}'</code></div>
                </div>
                <div class="line"><code>}</code></div>
            </div>
            <div class="line"><code>}</code></div>
            <div class="line"></div>
            <div class="line"><code class="keyword">dependencies</code> <code>{</code></div>
            <div class="indent">
                <div class="line">
                    <code class="keyword">${Builtin.defaultConfigurationFor(spec.getRepository())}</code>
                    <code class="string">'${spec.getFullyQualifiedName()}'</code>
                </div>
            </div>
            <div class="line"><code>}</code></div>
        </div>
    @endif
</div>
