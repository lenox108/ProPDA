package forpdateam.ru.forpda.model.repository.mentions

import android.content.SharedPreferences
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.model.data.remote.api.mentions.MentionsApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionsRepositoryTest {

    private val mentionsApi: MentionsApi = mockk()
    private val repository = MentionsRepository(mentionsApi)

    @Test
    fun getUnreadSnapshot_collectsUnreadTopicPostIds() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
            items.add(MentionItem().apply {
                state = MentionItem.STATE_READ
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=43"
            })
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_NEWS
                link = "https://4pda.to/2026/01/01/news"
            })
        }

        repository.refreshMentions(0)
        val snapshot = repository.getUnreadSnapshot()

        assertEquals(2, snapshot.unreadCount)
        assertEquals(listOf(42), snapshot.topicPostIds)
    }

    @Test
    fun getMentions_preservesUnreadAcrossRefreshUntilItemOpened() = runTest {
        every { mentionsApi.getMentions(0) } returnsMany listOf(
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_UNREAD
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
                    })
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_UNREAD
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=43"
                    })
                },
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
                    })
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=43"
                    })
                },
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
                    })
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=43"
                    })
                }
        )

        val firstLoad = repository.getMentions(0)
        val refresh = repository.getMentions(0)

        assertEquals(listOf(false, false), firstLoad.items.map { it.isRead })
        assertEquals(listOf(false, false), refresh.items.map { it.isRead })

        repository.markPostsRead(1, listOf(42))
        val afterOpenOne = repository.getMentions(0)

        assertEquals(listOf(true, false), afterOpenOne.items.map { it.isRead })
    }

    @Test
    fun refreshMentions_keepsPreviousListWhenRefreshReturnsEmpty() = runTest {
        // Симптом бага: список «Ответы» опустошается после захода. Причина — транзиентный
        // пустой/битый ответ (0 распарсенных строк) перетирал непустой список и кэш. 4pda
        // прочитанные упоминания из списка не убирает, так что 0 после N — почти всегда сбой.
        every { mentionsApi.getMentions(0) } returnsMany listOf(
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_UNREAD
                        type = MentionItem.TYPE_NEWS
                        link = "https://4pda.to/2026/07/02/458351/#comment10652404"
                    })
                },
                MentionsData(), // транзиентный пустой рефреш
        )

        val first = repository.refreshMentions(0)
        assertEquals(1, first.items.size)

        val second = repository.refreshMentions(0)
        // Не должен «мигнуть» пустым — сохраняем ранее загруженное упоминание.
        assertEquals(1, second.items.size)
    }

    @Test
    fun refreshMentions_showsEmptyWhenNoPreviousList() = runTest {
        // Настоящий пустой список (упоминаний не было) отдаём как есть — гард только про N→0.
        every { mentionsApi.getMentions(0) } returns MentionsData()
        assertEquals(0, repository.refreshMentions(0).items.size)
    }

    @Test
    fun refreshMentions_keepsPreviousListWhenFetchThrows() = runTest {
        // act=mentions отдал HTTP 404 (Cloudflare) → MentionsApi бросает; не опустошаем список.
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_NEWS
                link = "https://4pda.to/2026/07/02/458351/#comment10652404"
            })
        } andThenThrows forpdateam.ru.forpda.client.OkHttpResponseException(404, "Not Found", "url")

        assertEquals(1, repository.refreshMentions(0).items.size)
        assertEquals(1, repository.refreshMentions(0).items.size) // 404 — держим прошлый список
    }

    @Test
    fun markPostsRead_readsOnlyExactTopicPostKey() = runTest {
        every { mentionsApi.getMentions(0) } returnsMany listOf(
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_UNREAD
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
                    })
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_UNREAD
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=2&view=findpost&p=42"
                    })
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_UNREAD
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=43"
                    })
                },
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
                    })
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=2&view=findpost&p=42"
                    })
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=43"
                    })
                }
        )

        repository.getMentions(0)
        repository.markPostsRead(1, listOf(42))
        val afterRead = repository.getMentions(0)

        assertEquals(listOf(true, false, false), afterRead.items.map { it.isRead })
    }

    @Test
    fun markPostsRead_wrongPostDoesNotReadMention() = runTest {
        every { mentionsApi.getMentions(0) } returnsMany listOf(
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_UNREAD
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
                    })
                },
                MentionsData().apply {
                    items.add(MentionItem().apply {
                        state = MentionItem.STATE_READ
                        type = MentionItem.TYPE_TOPIC
                        link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
                    })
                }
        )

        repository.getMentions(0)
        repository.markPostsRead(1, listOf(43))
        val afterWrongPost = repository.getMentions(0)

        assertEquals(listOf(false), afterWrongPost.items.map { it.isRead })
    }

    @Test
    fun markPostsReadAndRecomputeUnreadSnapshot_exactPostFromAnyEntryClearsUnreadBadge() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }

        repository.getMentions(0)
        val (changed, snapshot) = repository.markPostsReadAndRecomputeUnreadSnapshot(1, listOf(41, 42, 43))

        assertEquals(true, changed)
        assertEquals(0, snapshot.unreadCount)
        assertEquals(emptyList<Int>(), snapshot.topicPostIds)
    }

    @Test
    fun markPostsReadAndRecomputeUnreadSnapshot_sameTopicDifferentPostDoesNotClearUnreadBadge() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }

        repository.getMentions(0)
        val (changed, snapshot) = repository.markPostsReadAndRecomputeUnreadSnapshot(1, listOf(43))

        assertEquals(false, changed)
        assertEquals(1, snapshot.unreadCount)
        assertEquals(listOf(42), snapshot.topicPostIds)
    }

    @Test
    fun markPostsReadAndRecomputeUnreadSnapshot_repeatedExactPostDoesNotDoubleDecrement() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }

        repository.getMentions(0)
        val first = repository.markPostsReadAndRecomputeUnreadSnapshot(1, listOf(42))
        val second = repository.markPostsReadAndRecomputeUnreadSnapshot(1, listOf(42))

        assertEquals(true, first.first)
        assertEquals(0, first.second.unreadCount)
        assertEquals(false, second.first)
        assertEquals(0, second.second.unreadCount)
    }

    @Test
    fun markTargetMissingAndRecomputeUnreadSnapshot_exactMissingTargetClearsOnlyMatchingMention() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=43"
            })
        }

        repository.getMentions(0)
        val (changed, snapshot) = repository.markPostsReadAndRecomputeUnreadSnapshot(1, listOf(42))

        assertEquals(true, changed)
        assertEquals(1, snapshot.unreadCount)
        assertEquals(listOf(43), snapshot.topicPostIds)
    }

    @Test
    fun recomputeUnreadSnapshot_usesLocalUnreadStoreWithoutPagingNetwork() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            pagination = pagination(current = 1, all = 2)
            items.add(MentionItem().apply {
                state = MentionItem.STATE_UNREAD
                type = MentionItem.TYPE_TOPIC
                link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
            })
        }

        repository.refreshMentions(0)
        val snapshot = repository.recomputeUnreadSnapshot()

        assertEquals(1, snapshot.unreadCount)
        assertEquals(listOf(42), snapshot.topicPostIds)
        verify(exactly = 0) { mentionsApi.getMentions(20) }
    }

    @Test
    fun readMention_survivesRepositoryReinitWithStaleUnreadServerState() = runTest {
        val preferences = InMemorySharedPreferences()
        val firstApi = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_UNREAD))
            }
        }
        val firstRepository = MentionsRepository(firstApi, preferences)

        firstRepository.refreshMentions(0)
        firstRepository.markPostsRead(1, listOf(42))

        val secondApi = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_UNREAD))
            }
        }
        val secondRepository = MentionsRepository(secondApi, preferences)
        val afterRestart = secondRepository.refreshMentions(0)

        assertEquals(listOf(true), afterRestart.items.map { it.isRead })
        assertEquals(0, secondRepository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun offlineCachedList_preservesLocalReadState() = runTest {
        val preferences = InMemorySharedPreferences()
        val api = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_UNREAD))
            }
        }
        val repository = MentionsRepository(api, preferences)

        repository.refreshMentions(0)
        repository.markPostsRead(1, listOf(42))
        val cached = repository.getCachedMentions(0)

        assertEquals(listOf(true), cached?.items?.map { it.isRead })
        assertEquals(0, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun staleServerRefresh_doesNotRemarkLocallyReadMentionUnread() = runTest {
        val preferences = InMemorySharedPreferences()
        val api = mockk<MentionsApi> {
            every { getMentions(0) } returnsMany listOf(
                    MentionsData().apply {
                        items.add(mention(1, 42, MentionItem.STATE_UNREAD))
                    },
                    MentionsData().apply {
                        items.add(mention(1, 42, MentionItem.STATE_UNREAD))
                    }
            )
        }
        val repository = MentionsRepository(api, preferences)

        repository.refreshMentions(0)
        repository.markPostsRead(1, listOf(42))
        val staleRefresh = repository.refreshMentions(0)

        assertEquals(listOf(true), staleRefresh.items.map { it.isRead })
        assertEquals(0, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun markMentionItemRead_marksTopicMentionFromLink() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(mention(1, 42, MentionItem.STATE_UNREAD))
        }

        repository.refreshMentions(0)
        val item = MentionItem().apply {
            state = MentionItem.STATE_UNREAD
            type = MentionItem.TYPE_TOPIC
            link = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42"
        }

        val (changed, snapshot) = repository.markMentionItemRead(item)

        assertEquals(true, changed)
        assertEquals(0, snapshot.unreadCount)
        assertEquals(listOf(true), repository.getCachedMentions(0)?.items?.map { it.isRead })
    }

    @Test
    fun unreadSnapshot_recomputesBadgeCountFromUnreadSourceOfTruth() = runTest {
        val preferences = InMemorySharedPreferences()
        val api = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_UNREAD))
                items.add(mention(1, 43, MentionItem.STATE_UNREAD))
            }
        }
        val repository = MentionsRepository(api, preferences)

        repository.refreshMentions(0)
        val (_, snapshot) = repository.markPostsReadAndRecomputeUnreadSnapshot(1, listOf(42))

        assertEquals(1, snapshot.unreadCount)
        assertEquals(listOf(43), snapshot.topicPostIds)
    }

    @Test
    fun eventUnreadOverride_keepsRowBoldWhenServerReturnsRead() = runTest {
        // act=mentions отдаёт упоминание прочитанным, но realtime-уведомление знает, что пост не открывали.
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(mention(1, 42, MentionItem.STATE_READ))
        }
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 42)
        val loaded = repository.refreshMentions(0)

        assertEquals(listOf(false), loaded.items.map { it.isRead })
        assertEquals(1, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun eventUnreadOverride_clearedWhenPostOpened() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(mention(1, 42, MentionItem.STATE_READ))
        }
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 42)
        repository.refreshMentions(0)
        repository.markPostsRead(1, listOf(42))
        val afterOpen = repository.refreshMentions(0)

        assertEquals(listOf(true), afterOpen.items.map { it.isRead })
        assertEquals(0, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun eventUnreadOverride_ignoredWhenAlreadyLocallyRead() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(mention(1, 42, MentionItem.STATE_READ))
        }
        val repository = MentionsRepository(mentionsApi)

        repository.refreshMentions(0)
        repository.markPostsRead(1, listOf(42))
        // Запоздавшее уведомление не должно воскрешать уже прочитанное упоминание.
        repository.markMentionUnreadFromNotification(1, 42)
        val afterLateEvent = repository.refreshMentions(0)

        assertEquals(listOf(true), afterLateEvent.items.map { it.isRead })
        assertEquals(0, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun eventUnreadOverride_survivesRepositoryReinitViaPrefs() = runTest {
        val preferences = InMemorySharedPreferences()
        val firstApi = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_READ))
            }
        }
        val firstRepository = MentionsRepository(firstApi, preferences)
        firstRepository.markMentionUnreadFromNotification(1, 42)

        val secondApi = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_READ))
            }
        }
        val secondRepository = MentionsRepository(secondApi, preferences)
        val afterRestart = secondRepository.refreshMentions(0)

        assertEquals(listOf(false), afterRestart.items.map { it.isRead })
        assertEquals(1, secondRepository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun removeUnreadFromEvent_dropsOverrideWithoutForcingPermanentRead() = runTest {
        every { mentionsApi.getMentions(0) } returnsMany listOf(
                MentionsData().apply { items.add(mention(1, 42, MentionItem.STATE_READ)) },
                MentionsData().apply { items.add(mention(1, 42, MentionItem.STATE_UNREAD)) }
        )
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 42)
        repository.refreshMentions(0)
        repository.removeUnreadFromEvent(1, 42)
        // Override снят, но не помечен прочитанным навсегда: серверный unread снова делает строку жирной.
        val afterServerUnread = repository.refreshMentions(0)

        assertEquals(listOf(false), afterServerUnread.items.map { it.isRead })
        assertEquals(1, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun reconcileWithServerMentionCount_clearsStaleOrphanOverrideWhenServerSaysZero() = runTest {
        // «Осиротевший» override: realtime сказал «непрочитано», но сервер в шапке форума уже 0
        // (прочитано на др. устройстве, READ-событие не дошло). Раньше держал бейдж «Ответы» на 1.
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(mention(1, 42, MentionItem.STATE_READ))
        }
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 42)
        repository.refreshMentions(0)
        assertEquals(1, repository.getUnreadSnapshot().unreadCount)

        val cleared = repository.reconcileWithServerMentionCount(0)

        assertEquals(true, cleared)
        assertEquals(0, repository.getUnreadSnapshot().unreadCount)
        assertEquals(listOf(true), repository.refreshMentions(0).items.map { it.isRead })
    }

    @Test
    fun reconcileWithServerMentionCount_noopWhenNothingLocallyUnread() = runTest {
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(mention(1, 42, MentionItem.STATE_READ))
        }
        val repository = MentionsRepository(mentionsApi)
        repository.refreshMentions(0)

        assertEquals(false, repository.reconcileWithServerMentionCount(0))
        assertEquals(0, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun reconcileWithServerMentionCount_keepsUnreadWhenServerStillCountsMention() = runTest {
        // Сервер подтверждает непрочитанное (>=1) — свежее упоминание не должно попасть под срез.
        every { mentionsApi.getMentions(0) } returns MentionsData().apply {
            items.add(mention(1, 42, MentionItem.STATE_READ))
        }
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 42)
        repository.refreshMentions(0)

        assertEquals(false, repository.reconcileWithServerMentionCount(1))
        assertEquals(1, repository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun clearTopicUnreadUpTo_readOnOtherDeviceClearsOverrideAndBadge() = runTest {
        // WS READ-событие по теме (прочитано на др. устройстве): messageId = «прочитано до поста включительно».
        every { mentionsApi.getMentions(0) } answers {
            MentionsData().apply { items.add(mention(1, 42, MentionItem.STATE_READ)) }
        }
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 42)
        repository.refreshMentions(0)
        assertEquals(1, repository.getUnreadSnapshot().unreadCount)

        val (changed, snapshot) = repository.clearTopicUnreadUpTo(1, 42)

        assertEquals(true, changed)
        assertEquals(0, snapshot.unreadCount)
        assertEquals(listOf(true), repository.refreshMentions(0).items.map { it.isRead })
    }

    @Test
    fun clearTopicUnreadUpTo_keepsMentionAfterReadBoundary() = runTest {
        // Прочитано до поста 42, а упоминание в посте 50 (ниже границы) — должно остаться жирным.
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 50)
        val (changed, snapshot) = repository.clearTopicUnreadUpTo(1, 42)

        assertEquals(false, changed)
        assertEquals(1, snapshot.unreadCount)
    }

    @Test
    fun clearTopicUnreadUpTo_unknownBoundaryClearsWholeTopicOnly() = runTest {
        val repository = MentionsRepository(mentionsApi)

        repository.markMentionUnreadFromNotification(1, 42)
        repository.markMentionUnreadFromNotification(2, 77)
        val (changed, snapshot) = repository.clearTopicUnreadUpTo(1, 0)

        assertEquals(true, changed)
        // Чужая тема не задета.
        assertEquals(1, snapshot.unreadCount)
        assertEquals(listOf(77), snapshot.topicPostIds)
    }

    @Test
    fun serverUnreadRow_survivesRepositoryReinitViaPrefs() = runTest {
        // GET act=mentions гасит unread-класс на сервере, поэтому непрочитанность строки должна
        // переживать рестарт процесса локально (иначе после воркера/рестарта строка серая при бейдже).
        val preferences = InMemorySharedPreferences()
        val firstApi = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_UNREAD))
            }
        }
        val firstRepository = MentionsRepository(firstApi, preferences)
        firstRepository.refreshMentions(0)
        assertEquals(1, firstRepository.getUnreadSnapshot().unreadCount)

        // «Рестарт»: новый инстанс, сервер уже отдаёт строку прочитанной (её погасил прошлый GET).
        val secondApi = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_READ))
            }
        }
        val secondRepository = MentionsRepository(secondApi, preferences)
        val afterRestart = secondRepository.refreshMentions(0)

        assertEquals(listOf(false), afterRestart.items.map { it.isRead })
        assertEquals(1, secondRepository.getUnreadSnapshot().unreadCount)
    }

    @Test
    fun clearAllLocalState_wipesKeysCacheAndPrefs() = runTest {
        val preferences = InMemorySharedPreferences()
        val api = mockk<MentionsApi> {
            every { getMentions(0) } returns MentionsData().apply {
                items.add(mention(1, 42, MentionItem.STATE_UNREAD))
            }
        }
        val repository = MentionsRepository(api, preferences)
        repository.refreshMentions(0)
        repository.markMentionUnreadFromNotification(1, 43)
        assertEquals(2, repository.getUnreadSnapshot().unreadCount)

        repository.clearAllLocalState()

        assertEquals(0, repository.getUnreadSnapshot().unreadCount)
        // Новый инстанс поверх тех же prefs ничего не восстанавливает.
        val fresh = MentionsRepository(mockk {
            every { getMentions(0) } returns MentionsData()
        }, preferences)
        assertEquals(0, fresh.getUnreadSnapshot().unreadCount)
    }

    private fun pagination(current: Int, all: Int) = forpdateam.ru.forpda.entity.remote.others.pagination.Pagination().apply {
        this.current = current
        this.all = all
        this.perPage = 20
    }

    private fun mention(topicId: Int, postId: Int, state: Int) = MentionItem().apply {
        this.state = state
        type = MentionItem.TYPE_TOPIC
        link = "https://4pda.to/forum/index.php?showtopic=$topicId&view=findpost&p=$postId"
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = applyPut(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = applyPut(key, values?.toSet())
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = applyPut(key, value)
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = applyPut(key, value)
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = applyPut(key, value)
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = applyPut(key, value)
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                key?.let { removals.add(it) }
            }
            override fun clear(): SharedPreferences.Editor = apply {
                clear = true
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clear) values.clear()
                removals.forEach { values.remove(it) }
                values.putAll(pending)
            }

            private fun applyPut(key: String?, value: Any?): SharedPreferences.Editor = apply {
                key?.let { pending[it] = value }
            }
        }
    }
}
