package io.micronaut.protobuf.json.transcoding

import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Requires
import io.micronaut.protobuf.transcoding.library.Book
import io.micronaut.protobuf.transcoding.library.CreateShelfRequest
import io.micronaut.protobuf.transcoding.library.DeleteBookRequest
import io.micronaut.protobuf.transcoding.library.GetShelfRequest
import io.micronaut.protobuf.transcoding.library.LibraryServiceGrpc
import io.micronaut.protobuf.transcoding.library.ListBooksRequest
import io.micronaut.protobuf.transcoding.library.ListBooksResponse
import io.micronaut.protobuf.transcoding.library.Shelf
import io.micronaut.protobuf.transcoding.library.UpdateBookRequest
import jakarta.inject.Singleton

@Singleton
@Requires(property = 'spec.name', value = 'GrpcTranscodingSpec')
class LibraryServiceImpl extends LibraryServiceGrpc.LibraryServiceImplBase {

    volatile Object lastRequest

    @Override
    void getShelf(GetShelfRequest request, StreamObserver<Shelf> responseObserver) {
        lastRequest = request
        if (request.name == 'shelves/missing') {
            responseObserver.onError(Status.NOT_FOUND.withDescription('No shelf ' + request.name).asRuntimeException())
            return
        }
        responseObserver.onNext(Shelf.newBuilder().setName(request.name).setTheme('Fiction').build())
        responseObserver.onCompleted()
    }

    @Override
    void listBooks(ListBooksRequest request, StreamObserver<ListBooksResponse> responseObserver) {
        lastRequest = request
        responseObserver.onNext(ListBooksResponse.newBuilder()
                .addBooks(Book.newBuilder().setId(1).setTitle('Dune').addAllTags(request.tagsList))
                .setNextPageToken(request.paging.token + '-next')
                .build())
        responseObserver.onCompleted()
    }

    @Override
    void createShelf(CreateShelfRequest request, StreamObserver<Shelf> responseObserver) {
        lastRequest = request
        responseObserver.onNext(request.shelf)
        responseObserver.onCompleted()
    }

    @Override
    void createBook(Book request, StreamObserver<Book> responseObserver) {
        lastRequest = request
        if (request.title.isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription('title is required').asRuntimeException())
            return
        }
        responseObserver.onNext(request.toBuilder().setId(42).build())
        responseObserver.onCompleted()
    }

    @Override
    void updateBook(UpdateBookRequest request, StreamObserver<Book> responseObserver) {
        lastRequest = request
        responseObserver.onNext(request.book)
        responseObserver.onCompleted()
    }

    @Override
    void deleteBook(DeleteBookRequest request, StreamObserver<Empty> responseObserver) {
        lastRequest = request
        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    @Override
    void getBookTitle(DeleteBookRequest request, StreamObserver<Book> responseObserver) {
        lastRequest = request
        responseObserver.onNext(Book.newBuilder().setId(request.id).setTitle('Title ' + request.id).build())
        responseObserver.onCompleted()
    }

    @Override
    void archiveBook(DeleteBookRequest request, StreamObserver<Book> responseObserver) {
        lastRequest = request
        responseObserver.onNext(Book.newBuilder().setId(request.id).setArchived(true).build())
        responseObserver.onCompleted()
    }

    @Override
    void searchBooks(ListBooksRequest request, StreamObserver<ListBooksResponse> responseObserver) {
        responseObserver.onNext(ListBooksResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    @Override
    void brokenRule(DeleteBookRequest request, StreamObserver<Book> responseObserver) {
        responseObserver.onNext(Book.getDefaultInstance())
        responseObserver.onCompleted()
    }

    @Override
    void streamBooks(ListBooksRequest request, StreamObserver<Book> responseObserver) {
        responseObserver.onCompleted()
    }
}
