package com.pulscen.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateTest {

    @Test
    fun choosesApkAssetForTheReleaseVersion() {
        val json = """
            {
              "tag_name":"v1.2.0",
              "draft":false,
              "prerelease":false,
              "assets":[
                {"name":"PulsCen-1.1.0.apk","url":"https://api.github.com/assets/1","size":100},
                {"name":"PulsCen-1.2.0.apk","url":"https://api.github.com/assets/2","size":200}
              ]
            }
        """.trimIndent()
        val release = GitHubUpdate.parseLatestRelease(json)
        assertNotNull(release)
        assertEquals("1.2.0", release!!.version)
        assertEquals("https://api.github.com/assets/2", release.apkUrl)
        assertEquals(200L, release.apkSize)
    }

    @Test
    fun ignoresDraftAndPrerelease() {
        assertTrue(GitHubUpdate.parseLatestRelease("""{"tag_name":"v2.0.0","draft":true}""") == null)
        assertTrue(GitHubUpdate.parseLatestRelease("""{"tag_name":"v2.0.0","prerelease":true}""") == null)
    }

    @Test
    fun comparesVersions() {
        assertTrue(GitHubUpdate.isNewer("1.0.1", "1.0.0"))
        assertFalse(GitHubUpdate.isNewer("1.0.0", "1.0.0"))
        assertTrue(GitHubUpdate.isNewer("1.1", "1.0.9"))
        assertEquals(0, GitHubUpdate.compareVersions("1.0", "1.0.0"))
        assertEquals("1.2.3", GitHubUpdate.normalizeTag("v1.2.3-beta"))
    }
}
