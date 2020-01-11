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

package com.android.example.paging.pagingwithnetwork.reddit.repository.inMemory.byPage

import androidx.lifecycle.MutableLiveData
import androidx.paging.LoadType.*
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult.Page
import com.android.example.paging.pagingwithnetwork.reddit.api.RedditApi
import com.android.example.paging.pagingwithnetwork.reddit.repository.NetworkState
import com.android.example.paging.pagingwithnetwork.reddit.vo.RedditPost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * A data source that uses the before/after keys returned in page requests.
 * <p>
 * See ItemKeyedSubredditDataSource
 */
class PageKeyedSubredditDataSource(
        private val redditApi: RedditApi,
        private val subredditName: String,
        private val networkDispatcher: CoroutineDispatcher
) : PagingSource<String, RedditPost>() {
    /**
     * There is no sync on the state because paging will always call loadInitial first then wait
     * for it to return some success value before calling loadAfter.
     */
    val networkState = MutableLiveData<NetworkState>()

    val initialLoad = MutableLiveData<NetworkState>()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, RedditPost> {
        return when (params.loadType) {
            REFRESH -> loadInitial(params)
            START -> throw IllegalStateException("Not implemented")
            END -> loadAfter(params)
        }
    }

    private suspend fun loadAfter(params: LoadParams<String>): LoadResult<String, RedditPost> {
        networkState.postValue(NetworkState.LOADING)
        try {
            val data = withContext(networkDispatcher) {
                redditApi.getTopAfter(subredditName, params.key!!, params.loadSize).data
            }

            networkState.postValue(NetworkState.LOADED)
            return Page(
                    data = data.children.map { it.data },
                    prevKey = null,
                    nextKey = data.after
            )
        } catch (e: HttpException) {
            networkState.postValue(NetworkState.error("error code: ${e.code()}"))
            return LoadResult.Error(e)
        } catch (e: IOException) {
            networkState.postValue(NetworkState.error(e.message ?: "unknown err"))
            return LoadResult.Error(e)
        }
    }

    private suspend fun loadInitial(params: LoadParams<String>): LoadResult<String, RedditPost> {
        networkState.postValue(NetworkState.LOADING)
        initialLoad.postValue(NetworkState.LOADING)

        try {
            val data = withContext(networkDispatcher) {
                redditApi.getTop(subreddit = subredditName, limit = params.loadSize).data
            }

            networkState.postValue(NetworkState.LOADED)
            initialLoad.postValue(NetworkState.LOADED)
            return Page(
                    data = data.children.map { it.data },
                    prevKey = data.before,
                    nextKey = data.after
            )
        } catch (e: HttpException) {
            networkState.postValue(NetworkState.error("error code: ${e.code()}"))
            return LoadResult.Error(e)
        } catch (ioException: IOException) {
            val error = NetworkState.error(ioException.message ?: "unknown error")
            networkState.postValue(error)
            initialLoad.postValue(error)
            return LoadResult.Error(ioException)
        }
    }
}
