@import net.derfruhling.spacemaven.LocalizationContext
@import net.derfruhling.spacemaven.SpecRef
@import java.util.List

@param LocalizationContext locale
@param List<SpecRef> specs
@param String repoName
@param String groupId
@param String artifactId
@param int page
@param long count

@template.page(title = "Package " + artifactId + " in group " + groupId, locale = locale, head = @`
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
    <div class="horizontal-rule"></div>
    <h3>${locale.lookup("head.header").replace("%%", groupId + ":" + artifactId)}</h3>
    <p>${locale.lookup("head.selectVersion")}</p>

    @template.code_blocks.repository(repositoryName = repoName)

    <ul>
        @for(var ref : specs)
            <li>
                <a href="/repo/${ref.getRepository()}/${ref.getGroupId()}/${ref.getArtifactId()}/${ref.getVersion()}">
                    @if(ref.getRepository().equals("gradle-plugins"))
                        <%-- gradle moment --%>
                        ${ref.getGroupId()}:${ref.getVersion()}
                    @else
                        ${ref.getFullyQualifiedName()}
                    @endif
                </a>
            </li>
        @endfor
    </ul>
`)