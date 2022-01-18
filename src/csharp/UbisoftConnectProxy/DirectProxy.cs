#nullable enable

using Microsoft.AspNetCore.Http;
using System;
using System.IO;
using System.Net.Http;
using System.Threading.Tasks;

namespace UbisoftConnectProxy
{
    //public class DirectProxy : IProxy
    //{
    //    public async Task ForwardAsync(HttpContext context)
    //    {
    //        try
    //        {
    //            using HttpClient httpClient = new();

    //            Uri targetUri = new("https://" + context.Request.Host + context.Request.Path +
    //                                (context.Request.QueryString.HasValue ? context.Request.QueryString.Value : ""));

    //            Console.WriteLine($"Forwarding {context.Request.Method} {targetUri}");

    //            HttpRequestMessage hrm = new(new(context.Request.Method), targetUri);

    //            foreach (var header in context.Request.Headers)
    //            {
    //                if (header.Key.StartsWith("Content"))
    //                {
    //                    continue;
    //                }

    //                foreach (var headerValue in header.Value)
    //                {
    //                    hrm.Headers.Add(header.Key, headerValue);
    //                }
    //            }

    //            await using (MemoryStream memoryStream = new())
    //            {
    //                await context.Request.Body.CopyToAsync(memoryStream);
    //                hrm.Content = new ByteArrayContent(memoryStream.ToArray());
    //            }

    //            using var response = await httpClient.SendAsync(hrm);

    //            var content = await response.Content.ReadAsByteArrayAsync();

    //            foreach (var header in response.Headers)
    //            {
    //                foreach (var headerValue in header.Value)
    //                {
    //                    context.Response.Headers.Add(header.Key, headerValue);
    //                }
    //            }

    //            context.Response.StatusCode = (int)response.StatusCode;
    //            Console.WriteLine("Status of response: " + response.StatusCode);

    //            context.Response.ContentType = response.Content.Headers.ContentType?.ToString()!;
    //            context.Response.ContentLength = response.Content.Headers.ContentLength;
    //            foreach (var encoding in response.Content.Headers.ContentEncoding)
    //            {
    //                context.Response.Headers.Add("Content-Encoding", encoding);
    //            }

    //            await context.Response.Body.WriteAsync(content);
    //            await context.Response.Body.FlushAsync();
    //        }
    //        catch (Exception e)
    //        {
    //            Console.WriteLine("ERROR: " + e);
    //        }
    //    }
    //}
}
