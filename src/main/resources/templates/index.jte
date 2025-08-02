@import kotlin.Pair
@import net.derfruhling.spacemaven.LocalizationContext
@import net.derfruhling.spacemaven.HeadRef
@import java.util.List

@param LocalizationContext locale
@param List<Pair<String, List<HeadRef>>> allSpecs

@template.page(title = "Spacemaven", locale = locale, head = @`
    <style>
        .horizontal-rule {
            border: 0 solid black;
            border-bottom-width: 1px;
            width: 300px;
            max-width: 100%;
            margin: 15px 5px;
        }

        li::marker {
            content: '>  ';
        }
    </style>
`, content = @`
    @for(var pair : allSpecs)
        <div class="repository">
            <div class="horizontal-rule"></div>
            <h3>${locale.lookup("index.repository." + pair.getFirst())}</h3>
            <p>${locale.lookup("index.repository." + pair.getFirst() + ".description")}</p>

            @template.code_blocks.repository(repositoryName = pair.getFirst())

            <ul>
                @for(var ref : pair.getSecond())
                    <li>
                        <a href="/repo/${pair.getFirst()}/${ref.getGroupId()}/${ref.getArtifactId()}">
                            @if(pair.getFirst().equals("gradle-plugins") && ref.getArtifactId().endsWith(".gradle.plugin"))
                                <%-- gradle moment --%>
                                ${ref.getGroupId()}
                            @else
                                ${ref.getFullyQualifiedName()}
                            @endif
                        </a>
                        @if(ref.getLatestReleaseVersion() != null)
                            ${ref.getLatestReleaseVersion()}
                        @endif
                    </li>
                @endfor
            </ul>
        </div>
    @endfor
`)