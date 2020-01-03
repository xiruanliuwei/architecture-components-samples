/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.paging.pagingwithnetwork.reddit.repository.inMemory.byItem

import androidx.lifecycle.MutableLiveData
import androidx.paging.LoadType.*
import androidx.paging.PagedSource
import androidx.paging.PagedSource.LoadResult.Page
import com.android.example.paging.pagingwithnetwork.reddit.api.RedditApi
import com.android.example.paging.pagingwithnetwork.reddit.repository.NetworkState
import com.android.example.paging.pagingwithnetwork.reddit.vo.RedditPost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * A data source that uses the "name" field of posts as the key for next/prev pages.
 * <p>
 * Note that this is not the correct consumption of the Reddit API but rather shown here as an
 * alternative implementation which might be more suitable for your backend.
 * see PageKeyedSubredditDataSource for the other sample.
 */
class ItemKeyedSubredditDataSource(
        private val redditApi: RedditApi,
        private val subredditName: String,
        private val networkDispatcher: CoroutineDispatcher
) : PagedSource<String, RedditPost>() {
    /**
     * There is no sync on the state because paging will always call loadInitial first then wait
     * for it to return some success value before calling loadAfter and we don't support loadBefore
     * in this example.
     * <p>
     * See BoundaryCallback example for a more complete example on syncing multiple network states.
     */
    val networkState = MutableLiveData<NetworkState>()

    val initialLoad = MutableLiveData<NetworkState>()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, RedditPost> {
        return when (params.loadType) {
            REFRESH -> loadInitial(params)
            // Ignored, since we only ever append to our initial load.
            START -> throw IllegalStateException("Ignored")
            END -> loadAfter(params)
        }
    }

    private suspend fun loadAfter(params: LoadParams<String>): LoadResult<String, RedditPost> {
        // set network value to loading.
        networkState.postValue(NetworkState.LOADING)
        // even though we are using async retrofit API here, we could also use sync
        // it is just different to show that the callback can be called async.
        try {
            val items = withContext(networkDispatcher) {
                redditApi.getTopAfter(subredditName, params.key!!, params.loadSize)
                        .data
                        .children
                        .map { it.data }
            }

            networkState.postValue(NetworkState.LOADED)

            return Page(
                    data = items,
                    prevKey = items.firstOrNull()?.name,
                    nextKey = items.lastOrNull()?.name
            )
        } catch (e: HttpException) {
            networkState.postValue(NetworkState.error("error code: ${e.code()}"))
            return LoadResult.Error(e)
        } catch (e: Throwable) {
            // publish the error
            networkState.postValue(NetworkState.error(e.message ?: "unknown err"))
            return LoadResult.Error(e)
        }
    }

    override fun getRefreshKeyFromPage(indexInPage: Int, page: Page<String, RedditPost>): String? {
        /**
         * The name field is a unique identifier for post items.
         * (no it is not the title of the post :) )
         * https://www.reddit.com/dev/api
         */
        return page.data[indexInPage].name
    }

    private suspend fun loadInitial(params: LoadParams<String>): LoadResult<String, RedditPost> {
        // update network states.
        // we also provide an initial load state to the listeners so that the UI can know when the
        // very first list is loaded.
        networkState.postValue(NetworkState.LOADING)
        initialLoad.postValue(NetworkState.LOADING)

        try {
            val items = redditApi.getTop(subreddit = subredditName, limit = params.loadSize)
                    .data
                    .children
                    .map { it.data }

            networkState.postValue(NetworkState.LOADED)
            initialLoad.postValue(NetworkState.LOADED)

            return Page(
                    data = items,
                    prevKey = null,
                    nextKey = items.lastOrNull()?.name
            )
        } catch (ioException: IOException) {
            val error = NetworkState.error(ioException.message ?: "unknown error")
            networkState.postValue(error)
            initialLoad.postValue(error)
            return LoadResult.Error(ioException)
        }
    }

}
