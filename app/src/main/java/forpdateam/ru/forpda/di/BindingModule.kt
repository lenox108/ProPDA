package forpdateam.ru.forpda.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ErrorHandler
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.app.profile.UserHolder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {

    @Binds
    @Singleton
    abstract fun bindErrorHandler(errorHandler: ErrorHandler): IErrorHandler

    @Binds
    @Singleton
    abstract fun bindUserHolder(userHolder: UserHolder): IUserHolder
}
