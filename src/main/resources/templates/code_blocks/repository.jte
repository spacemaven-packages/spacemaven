@import net.derfruhling.spacemaven.Builtin

@param String repositoryName

<div>
    @if(repositoryName.equals("gradle-plugins"))
        <div class="code-block">
            <code class="line comment">// settings.gradle</code>
            <div class="line"><code class="keyword">pluginManagement</code> <code>{</code></div>
            <div class="indent">
                <div class="line"><code class="keyword">repositories</code> <code>{</code></div>
                <div class="indent">
                    <div class="line"><code class="keyword">maven</code> <code>{</code></div>
                    <div class="indent">
                        <div class="line"><code class="keyword">name</code> <code class="string">'spacemaven-${repositoryName}'</code></div>
                        <div class="line"><code class="keyword">url</code> <code class="string">'${Builtin.repositoryUrlFor(repositoryName)}'</code></div>
                    </div>
                    <div class="line"><code>}</code></div>
                </div>
                <div class="line"><code>}</code></div>
            </div>
            <div class="line"><code>}</code></div>
            <div class="line"></div>
            <code class="line comment">// build.gradle</code>
            <div class="line"><code class="keyword">plugins</code> <code>{</code></div>
            <div class="indent">
                <code class="line comment">// id '...' version '...'</code>
            </div>
            <div class="line"><code>}</code></div>
        </div>
    @else
        <div class="code-block">
            @if (repositoryName.equals("tools"))
                <code class="line comment">// settings.gradle</code>
                <div class="line"><code class="keyword">pluginManagement</code> <code>{</code></div>
                <div class="indent">
                    <div class="line"><code class="keyword">repositories</code> <code>{</code></div>
                    <div class="indent">
                        <div class="line"><code class="keyword">maven</code> <code>{</code></div>
                        <div class="indent">
                            <div class="line"><code class="keyword">name</code> <code class="string">'spacemaven-gradle-plugins'</code></div>
                            <div class="line"><code class="keyword">url</code> <code class="string">'${Builtin.repositoryUrlFor("gradle-plugins")}'</code></div>
                        </div>
                        <div class="line"><code>}</code></div>
                    </div>
                    <div class="line"><code>}</code></div>
                </div>
                <div class="line"><code>}</code></div>
                <div class="line"></div>
            @endif
            <code class="line comment">// build.gradle</code>
            @if (repositoryName.equals("tools"))
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
                    <div class="line"><code class="keyword">name</code> <code class="string">'spacemaven-${repositoryName}'</code></div>
                    <div class="line"><code class="keyword">url</code> <code class="string">'${Builtin.repositoryUrlFor(repositoryName)}'</code></div>
                </div>
                <div class="line"><code>}</code></div>
            </div>
            <div class="line"><code>}</code></div>
            <div class="line"></div>
            <div class="line"><code class="keyword">dependencies</code> <code>{</code></div>
            <div class="indent">
                <div class="line">
                    <code class="comment">// ${Builtin.defaultConfigurationFor(repositoryName)} '...'</code>
                </div>
            </div>
            <div class="line"><code>}</code></div>
        </div>
    @endif
</div>