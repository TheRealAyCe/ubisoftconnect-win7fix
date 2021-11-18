#nullable enable

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace UbisoftConnectProxy.JavaInterop.Dtos
{
    public interface IDataObject
    {
        Task ReadAsync(Stream stream);
        void Write(Stream stream);
    }

    public class HeaderDto : IDataObject
    {
        public string Name { get; set; } = null!;
        public string[] Values { get; set; } = null!;

        public async Task ReadAsync(Stream stream)
        {
            Name = await stream.ReadJUTF8Async();
            Values = await stream.ReadListAsync(() => stream.ReadJUTF8Async());
        }

        public void Write(Stream stream)
        {
            stream.WriteJUTF8(Name);
            stream.WriteList(Values, stream.WriteJUTF8);
        }
    }

    public abstract class HttpDataDto : IDataObject
    {
        public HeaderDto[] Headers { get; set; } = null!;
        public byte[] Content { get; set; } = null!;

        public virtual async Task ReadAsync(Stream stream)
        {
            Headers = await stream.ReadListAsync(async () =>
            {
                var header = new HeaderDto();
                await header.ReadAsync(stream);
                return header;
            });
            Content = await stream.ReadFullyAsync(await stream.ReadJIntAsync());
        }

        public virtual void Write(Stream stream)
        {
            stream.WriteList(Headers, x => x.Write(stream));
            stream.WriteJInt(Content.Length);
            stream.Write(Content);
        }
    }

    public abstract class MessageDto<T> : IDataObject where T : IDataObject, new()
    {
        public int RequestId { get; set; }
        public T Data { get; set; } = default!;

        public async Task ReadAsync(Stream stream)
        {
            RequestId = await stream.ReadJIntAsync();
            Data = new T();
            await Data.ReadAsync(stream);
        }

        public void Write(Stream stream)
        {
            stream.WriteJInt(RequestId);
            Data.Write(stream);
        }
    }

    public class ResponseDto : MessageDto<ResponseDataDto>
    {
        public const byte MsgId = 1;
    }

    public static class JavaDnsReady
    {
        public const byte MsgId = 2;
    }

    public class RequestDto : MessageDto<RequestDataDto>
    {
        public const byte MsgId = 10;
    }

    public enum Ready : byte
    {
        Hosts, Starting, Running
    }

    public class WebserverReady : IDataObject
    {
        public const byte MsgId = 11;

        public Ready Ready { get; set; }

        public async Task ReadAsync(Stream stream)
        {
            Ready = (Ready) await stream.ReadByteAsync();
        }

        public void Write(Stream stream)
        {
            stream.WriteByte((byte)Ready);
        }
    }

    public class WebserverErrorDto : IDataObject
    {
        public const byte MsgId = 12;

        public bool Fatal;
        public string Text { get; set; } = "";

        public async Task ReadAsync(Stream stream)
        {
            Fatal = await stream.ReadByteAsync() == 1;
            Text = await stream.ReadJUTF8Async();
        }

        public void Write(Stream stream)
        {
            stream.WriteByte(Fatal ? (byte)1 : (byte)0);
            stream.WriteJUTF8(Text);
        }
    }

    public class RequestDataDto : HttpDataDto
    {
        public string Uri { get; set; } = null!;
        public string Method { get; set; } = null!;

        public override async Task ReadAsync(Stream stream)
        {
            Uri = await stream.ReadJUTF8Async();
            Method = await stream.ReadJUTF8Async();
            await base.ReadAsync(stream);
        }

        public override void Write(Stream stream)
        {
            stream.WriteJUTF8(Uri);
            stream.WriteJUTF8(Method);
            base.Write(stream);
        }
    }

    public class ResponseDataDto : HttpDataDto
    {
        public int StatusCode { get; set; }

        public override async Task ReadAsync(Stream stream)
        {
            StatusCode = await stream.ReadJIntAsync();
            await base.ReadAsync(stream);
        }

        public override void Write(Stream stream)
        {
            stream.WriteJInt(StatusCode);
            base.Write(stream);
        }
    }

    public static class JavaBinaryStuff
    {
        public static void WriteList<T>(this Stream stream, IReadOnlyList<T> list, Action<T> writeItem)
        {
            stream.WriteJInt(list.Count);
            foreach (var item in list)
            {
                writeItem(item);
            }
        }

        public static async Task<T[]> ReadListAsync<T>(this Stream stream, Func<Task<T>> readItem)
        {
            var list = new T[await stream.ReadJIntAsync()];
            for (int i = 0; i < list.Length; i++)
            {
                list[i] = await readItem();
            }
            return list;
        }

        public static void WriteJInt(this Stream stream, int value)
        {
            stream.WriteByte((byte)(value >> 24));
            stream.WriteByte((byte)(value >> 16));
            stream.WriteByte((byte)(value >> 8));
            stream.WriteByte((byte)(value >> 0));
        }

        public static void WriteJUShort(this Stream stream, ushort value)
        {
            stream.WriteByte((byte)(value >> 8));
            stream.WriteByte((byte)(value >> 0));
        }

        public static void WriteJUTF8(this Stream stream, string value)
        {
            var bytes = Encoding.UTF8.GetBytes(value);
            if (bytes.Length > ushort.MaxValue)
            {
                throw new IOException($"String value too long ({bytes.Length})");
            }
            stream.WriteJUShort((ushort)bytes.Length);
            stream.Write(bytes);
        }

        public static async Task<int> ReadJIntAsync(this Stream stream)
        {
            byte[] data = await stream.ReadFullyAsync(4);
            return (data[0] << 24)
                | (data[1] << 16)
                | (data[2] << 8)
                | (data[3] << 0);
        }

        public static async Task<byte> ReadByteAsync(this Stream stream)
        {
            return (await stream.ReadFullyAsync(1))[0];
        }

        public static async Task<ushort> ReadJUShortAsync(this Stream stream)
        {
            byte[] data = await stream.ReadFullyAsync(2);
            return (ushort)((data[0] << 8)
                | (data[1] << 0));
        }

        public static async Task<string> ReadJUTF8Async(this Stream stream)
        {
            int length = await stream.ReadJUShortAsync();
            byte[] bytes = await stream.ReadFullyAsync(length);
            return Encoding.UTF8.GetString(bytes);
        }

        public static async Task<byte[]> ReadFullyAsync(this Stream stream, int numBytes, CancellationToken cancellationToken = default)
        {
            var buffer = new byte[numBytes];
            await stream.ReadFullyAsync(buffer, 0, numBytes, cancellationToken);
            return buffer;
        }

        public static async Task ReadFullyAsync(this Stream stream, byte[] buffer, int offset, int length, CancellationToken cancellationToken = default)
        {
            await stream.ReadFullyAsync(buffer.AsMemory(offset, length), cancellationToken);
        }

        public static async Task ReadFullyAsync(this Stream stream, Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            int offset = 0;
            while (offset < buffer.Length)
            {
                int read = await stream.ReadAsync(buffer[offset..], cancellationToken);
                if (read <= 0)
                {
                    throw new EndOfStreamException();
                }
                offset += read;
            }
        }
    }
}
